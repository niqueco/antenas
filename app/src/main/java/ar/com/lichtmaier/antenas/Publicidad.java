package ar.com.lichtmaier.antenas;

import android.app.Activity;
import android.arch.lifecycle.DefaultLifecycleObserver;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
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

class Publicidad implements DefaultLifecycleObserver
{
	private final static Map<String, Intersticial> intersticiales = new HashMap<>();

	private final AdView adView;
	private boolean loaded;
	private final Lifecycle lifecycle;

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
				.addTestDevice("8B1260447C80CBDD838ECB86F1DA61B1") // Galaxy S8
				.addTestDevice(AdRequest.DEVICE_ID_EMULATOR);
	}

	@Override
	public void onPause(@NonNull LifecycleOwner owner)
	{
		if(adView != null)
			adView.pause();
	}

	@Override
	public void onResume(@NonNull LifecycleOwner owner)
	{
		if(adView != null)
			adView.resume();
	}

	@Override
	public void onDestroy(@NonNull LifecycleOwner owner)
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
