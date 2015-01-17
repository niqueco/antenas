package ar.com.lichtmaier.antenas;

import android.app.Activity;
import android.location.Location;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

class Publicidad
{
	private AdView adView;

	Publicidad(Activity act, String adUnitId)
	{
		if(act instanceof UnaAntenaActivity)
			return;
		adView = new AdView(act);
		adView.setAdUnitId(adUnitId);
		adView.setAdSize(AdSize.BANNER);
		ViewGroup v = (ViewGroup)act.findViewById(R.id.principal);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				(int)(50 * act.getResources().getDisplayMetrics().density), 0);
		v.addView(adView, params);
	}

	void load(Location loc)
	{
		if(adView == null)
			return;
		AdRequest.Builder builder = crearAdRequestBuilder()
				.addKeyword("antenna")
				.addKeyword("tv")
				.addKeyword("technology")
				.addKeyword("digital tv")
				.addKeyword("ota")
				.addKeyword("cordcutter");
		if(loc != null)
			builder.setLocation(loc);
		adView.loadAd(builder.build());
	}

	static AdRequest.Builder crearAdRequestBuilder()
	{
		return new AdRequest.Builder()
				.addTestDevice("341C11CC2A47D92A590EF87DA9E8125E")
				.addTestDevice("DE769C8D98D3DACE221A6675804E8CAA")
				.addTestDevice("C34A7B13BA1B16DE0CABA7247F94C289")
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
}
