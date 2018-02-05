package ar.com.lichtmaier.antenas;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.arch.lifecycle.LiveData;
import android.content.*;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

import com.android.vending.billing.IInAppBillingService;
import com.crashlytics.android.Crashlytics;

import java.util.ArrayList;

import static android.app.Activity.RESULT_OK;


class AyudanteDePagos extends LiveData<Boolean> implements ServiceConnection
{
	private static final String TAG = "antenas-pagos";

	private static final String ID_PRODUCTO = "pro";
	private static final int REQUEST_CODE_COMPRAR = 152;

	private static final int RESULT_ITEM_ALREADY_OWNED = 7;

	private final Context context;
	private IInAppBillingService pagosDeGoogle;

	private static AyudanteDePagos instance;

	private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			verificarCompras();
		}
	};

	private AyudanteDePagos(Context context)
	{
		this.context = context.getApplicationContext();
	}

	public static AyudanteDePagos dameInstancia(Context context)
	{
		if(instance == null)
			instance = new AyudanteDePagos(context);
		return instance;
	}

	@Override
	public void onServiceDisconnected(ComponentName name)
	{
		pagosDeGoogle = null;
		Crashlytics.log(Log.WARN, TAG, "servicio de pagos offline");
	}

	@SuppressLint("StaticFieldLeak")
	@Override
	public void onServiceConnected(ComponentName name, IBinder service)
	{
		pagosDeGoogle = IInAppBillingService.Stub.asInterface(service);
		if(BuildConfig.DEBUG && Boolean.TRUE.equals(getValue()))
			return;
		verificarCompras();
	}

	private void verificarCompras()
	{
		new AsyncTask<Void, Void, Boolean>() {
			@Override
			protected Boolean doInBackground(Void... voids)
			{
				ArrayList<String> compras = null;
				try
				{
					Bundle resp = pagosDeGoogle.getPurchases(3, BuildConfig.APPLICATION_ID, "inapp", null);
					int responseCode = resp.getInt("RESPONSE_CODE", -1);
					if(responseCode != 0)
					{
						Log.e(TAG, "Obteniendo información sobre el pago: responseCode = " + responseCode);
						return false;
					}
					compras = resp.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
					if(Log.isLoggable(TAG, Log.DEBUG))
						Log.d(TAG, "compras: " + compras);
				} catch(Exception e)
				{
					Log.e(TAG, "Obteniendo información sobre el pago:", e);
					Crashlytics.logException(e);
				}
				return compras != null && compras.contains(ID_PRODUCTO);
			}

			@Override
			protected void onPostExecute(Boolean pro)
			{
				setValue(pro);
			}
		}.execute();
	}

	@Override
	protected void onActive()
	{
		Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
		serviceIntent.setPackage("com.android.vending");
		context.bindService(serviceIntent, this, Context.BIND_AUTO_CREATE);

		IntentFilter promoFilter = new IntentFilter("com.android.vending.billing.PURCHASES_UPDATED");
		context.registerReceiver(broadcastReceiver, promoFilter);
	}

	void pagar(FragmentActivity act)
	{
		if(BuildConfig.DEBUG)
		{
			setValue(true);
			return;
		}

		if(pagosDeGoogle == null)
		{
			Toast.makeText(context, R.string.no_se_puede_pagar, Toast.LENGTH_SHORT).show();
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
					Toast.makeText(context, "Ya está usando la versión Pro", Toast.LENGTH_SHORT).show();

					pagosDeGoogle.consumePurchase(3, BuildConfig.APPLICATION_ID, ID_PRODUCTO);
				}
				else
					Log.e(TAG, "Comprando RESPONSE_CODE = " + res);
				return;
			}
			PendingIntent pendingIntent = b.getParcelable("BUY_INTENT");
			assert pendingIntent != null;
			act.startIntentSenderForResult(pendingIntent.getIntentSender(), REQUEST_CODE_COMPRAR, new Intent(), 0, 0, 0);
		} catch(RemoteException|IntentSender.SendIntentException e)
		{
			Crashlytics.logException(e);
			Log.e(TAG, "comprando", e);
		}
	}

	@Override
	protected void onInactive()
	{
		context.unregisterReceiver(broadcastReceiver);
		context.unbindService(this);
	}

	@SuppressWarnings("UnusedParameters")
	boolean onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if(requestCode != REQUEST_CODE_COMPRAR)
			return false;

		if(resultCode == RESULT_OK)
		{
			Toast.makeText(context, R.string.thankyou, Toast.LENGTH_SHORT).show();
			setValue(true);
		}
		return true;
	}
}
