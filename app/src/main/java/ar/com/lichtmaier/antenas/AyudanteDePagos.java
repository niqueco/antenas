package ar.com.lichtmaier.antenas;

import android.app.PendingIntent;
import android.content.*;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.android.vending.billing.IInAppBillingService;
import com.google.firebase.crash.FirebaseCrash;

import java.util.ArrayList;

import static android.app.Activity.RESULT_OK;


class AyudanteDePagos implements ServiceConnection
{
	private static final String ID_PRODUCTO = "pro";
	private static final int REQUEST_CODE_COMPRAR = 152;

	private static final int RESULT_ITEM_ALREADY_OWNED = 7;

	private final FragmentActivity activity;
	private final CallbackPagos callback;
	private IInAppBillingService pagosDeGoogle;
	private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			callback.esPro(intent.getBooleanExtra("pro", false));
		}
	};

	@Nullable
	Boolean pro;

	private void mandarBroadcast(boolean pro)
	{
		this.pro = pro;
		Intent intent = new Intent("pro");
		intent.putExtra("pro", pro);
		LocalBroadcastManager.getInstance(activity).sendBroadcast(intent);
	}

	AyudanteDePagos(FragmentActivity activity, CallbackPagos callback)
	{
		this.activity = activity;
		this.callback = callback;
	}

	@Override
	public void onServiceDisconnected(ComponentName name)
	{
		pagosDeGoogle = null;
		FirebaseCrash.logcat(Log.WARN, "antenas", "servicio de pagos offline");
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder service)
	{
		pagosDeGoogle = IInAppBillingService.Stub.asInterface(service);
		new AsyncTask<Void, Void, Boolean>() {
			@Override
			protected Boolean doInBackground(Void... voids)
			{
				ArrayList<String> compras = null;
				try
				{
					Bundle resp = pagosDeGoogle.getPurchases(3, BuildConfig.APPLICATION_ID, "inapp", null);
					Log.i("antenas", "resp: " + resp.keySet());
					int responseCode = resp.getInt("RESPONSE_CODE", -1);
					if(responseCode != 0)
					{
						Log.e("antenas", "Obteniendo información sobre el pago: responseCode = " + responseCode);
						return false;
					}
					compras = resp.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
					Log.i("antenas", "compras: " + compras);
				} catch(Exception e)
				{
					Log.e("antenas", "Obteniendo información sobre el pago:", e);
					FirebaseCrash.report(e);
				}
				boolean pro = compras != null && compras.contains(ID_PRODUCTO);
				mandarBroadcast(pro);
				return pro;
			}
		}.execute();
	}

	void registrarServicio()
	{
		Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
		serviceIntent.setPackage("com.android.vending");
		activity.bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);
		IntentFilter intentFilter = new IntentFilter("pro");
		LocalBroadcastManager.getInstance(activity).registerReceiver(broadcastReceiver, intentFilter);
	}

	void pagar()
	{
		if(BuildConfig.DEBUG)
		{
			mandarBroadcast(true);
			return;
		}

		if(pagosDeGoogle == null)
		{
			Toast.makeText(activity, R.string.no_se_puede_pagar, Toast.LENGTH_SHORT).show();
			return;
		}
		try
		{
			Bundle b = pagosDeGoogle.getBuyIntent(3, BuildConfig.APPLICATION_ID, ID_PRODUCTO, "inapp", "");
			int res = b.getInt("RESPONSE_CODE", -1);
			if(res != 0)
			{
				if(res == RESULT_ITEM_ALREADY_OWNED)
				{
					Toast.makeText(activity, "Ya está usando la versión Pro", Toast.LENGTH_SHORT).show();

					pagosDeGoogle.consumePurchase(3, BuildConfig.APPLICATION_ID, ID_PRODUCTO);
				}
				else
					Log.e("antenas", "Comprando RESPONSE_CODE = " + res);
				return;
			}
			PendingIntent pendingIntent = b.getParcelable("BUY_INTENT");
			assert pendingIntent != null;
			activity.startIntentSenderForResult(pendingIntent.getIntentSender(), REQUEST_CODE_COMPRAR, new Intent(), 0, 0, 0);
		} catch(RemoteException|IntentSender.SendIntentException e)
		{
			FirebaseCrash.report(e);
			Log.e("antenas", "comprando", e);
		}
	}

	void destroy()
	{
		activity.unbindService(this);
		LocalBroadcastManager.getInstance(activity).unregisterReceiver(broadcastReceiver);
	}

	@SuppressWarnings("UnusedParameters")
	boolean onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if(requestCode != REQUEST_CODE_COMPRAR)
			return false;

		if(resultCode == RESULT_OK)
		{
			Toast.makeText(activity, R.string.thankyou, Toast.LENGTH_SHORT).show();
			mandarBroadcast(true);
		}
		return true;
	}

	interface CallbackPagos
	{
		void esPro(boolean pro);
	}
}
