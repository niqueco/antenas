package ar.com.lichtmaier.antenas;

import android.app.Activity;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.OnLifecycleEvent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.android.gms.ads.*;

import java.util.HashMap;
import java.util.Map;

class Publicidad implements LifecycleObserver
{
	private final static Map<String, Intersticial> intersticiales = new HashMap<>();

	private final AdView adView;
	private boolean loaded;
	private Lifecycle lifecycle;

	Publicidad(Activity act, Lifecycle lifecycle, String adUnitId)
	{
		this.lifecycle = lifecycle;
		ViewGroup v = act.findViewById(R.id.principal_para_pub);
		if(v == null)
			v = act.findViewById(R.id.principal);
		if(v == null)
		{
			adView = null;
			return;
		}
		adView = new AdView(act);
		adView.setAdUnitId(adUnitId);
		adView.setAdSize(AdSize.SMART_BANNER);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT, 0);
		v.addView(adView, params);

		lifecycle.addObserver(this);
	}

	void load(Location loc)
	{
		if(adView == null || loaded)
			return;
		loaded = true;
		adView.loadAd(getAdRequest(loc));
	}

	@NonNull
	private static AdRequest getAdRequest(Location loc)
	{
		AdRequest.Builder builder = crearAdRequestBuilder()
				.addKeyword("antenna")
				.addKeyword("tv")
				.addKeyword("technology")
				.addKeyword("digital tv")
				.addKeyword("ota")
				.addKeyword("cordcutter");
		if(loc != null)
			builder.setLocation(loc);
		return builder.build();
	}

	static AdRequest.Builder crearAdRequestBuilder()
	{
		return new AdRequest.Builder()
				.addTestDevice("341C11CC2A47D92A590EF87DA9E8125E")
				.addTestDevice("DE769C8D98D3DACE221A6675804E8CAA")
				.addTestDevice("C34A7B13BA1B16DE0CABA7247F94C289")
				.addTestDevice("F46546E8E614B8A8886A62006FA3AFB7") // Nexus 6P
				.addTestDevice(AdRequest.DEVICE_ID_EMULATOR);
	}

	@OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
	void onPause()
	{
		if(adView != null)
			adView.pause();
	}

	@OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
	void onResume()
	{
		if(adView != null)
			adView.resume();
	}

	@OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
	void onDestroy()
	{
		if(adView != null)
			adView.destroy();
		lifecycle.removeObserver(this);
	}

	public int getHeight()
	{
		return adView == null ? 0 : adView.getHeight();
	}

	void sacar()
	{
		lifecycle.removeObserver(this);
		if(adView == null)
			return;
		((ViewGroup)adView.getParent()).removeView(adView);
		adView.destroy();
	}

	Intersticial crearIntersticial(Activity activity, String adUnitId)
	{
		Intersticial intersticial = intersticiales.get(adUnitId);
		if(intersticial == null)
		{
			intersticial = new Intersticial(activity, adUnitId);
			intersticiales.put(adUnitId, intersticial);
		}
		return intersticial;
	}

	static class Intersticial extends AdListener
	{
		final private InterstitialAd ad;
		private Activity activity;
		private Intent intent;
		private Callback callback;

		Intersticial(Context context, String adUnitId)
		{
			ad = new InterstitialAd(context.getApplicationContext());
			ad.setAdUnitId(adUnitId);
			ad.setAdListener(this);
			pedir();
		}

		@Override
		public void onAdClosed()
		{
			if(intent != null)
			{
				activity.startActivity(intent);
				intent = null;
				activity = null;
			} else if (callback != null)
			{
				callback.run(true);
				intent = null;
			}
		}

		private void pedir()
		{
			ad.loadAd(getAdRequest(null));
		}

		void siguienteActividad(Activity activity, Intent intent, Bundle options)
		{
			if(ad.isLoaded())
			{
				this.intent = intent;
				this.callback = null;
				this.activity = activity;
				ad.show();
			} else
			{
				ActivityCompat.startActivity(activity, intent, options);
				this.intent = null;
			}
		}

		void mostrar(Callback callback)
		{
			if(ad.isLoaded())
			{
				this.intent = null;
				this.callback = callback;
				this.activity = null;
				ad.show();
			} else
			{
				callback.run(false);
				this.callback = null;
			}
		}

		interface Callback
		{
			void run(boolean huboAviso);
		}
	}
}
