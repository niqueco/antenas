package ar.com.lichtmaier.antenas;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.support.annotation.NonNull;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.android.gms.ads.*;

class Publicidad
{
	private final AdView adView;
	private boolean loaded;

	Publicidad(Activity act, String adUnitId)
	{
		ViewGroup v = (ViewGroup)act.findViewById(R.id.principal_para_pub);
		if(v == null)
			v = (ViewGroup)act.findViewById(R.id.principal);
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
				.addTestDevice("AF97388EB284AD8A824F40233476DB51") // Nexus 6P
				.addTestDevice(AdRequest.DEVICE_ID_EMULATOR);
	}

	void onPause()
	{
		if(adView != null)
			adView.pause();
	}

	void onResume()
	{
		if(adView != null)
			adView.resume();
	}

	void onDestroy()
	{
		if(adView != null)
			adView.destroy();
	}

	public int getHeight()
	{
		return adView == null ? 0 : adView.getHeight();
	}

	void sacar()
	{
		if(adView == null)
			return;
		((ViewGroup)adView.getParent()).removeView(adView);
		adView.destroy();
	}

	Intersticial crearIntersticial(Activity activity, String adUnitId)
	{
		return new Intersticial(activity, adUnitId);
	}

	static class Intersticial extends AdListener
	{
		final InterstitialAd ad;
		final Activity activity;
		Intent intent;

		Intersticial(Activity activity, String adUnitId)
		{
			this.activity = activity;
			ad = new InterstitialAd(activity);
			ad.setAdUnitId(adUnitId);
			ad.setAdListener(this);
			pedir();
		}

		@Override
		public void onAdClosed()
		{
			activity.startActivity(intent);
		}

		private void pedir()
		{
			ad.loadAd(getAdRequest(null));
		}

		void siguienteActividad(Intent intent)
		{
			this.intent = intent;
			if(ad.isLoaded())
			{
				ad.show();
			} else
			{
				activity.startActivity(intent);
				this.intent = null;
			}
		}
	}
}
