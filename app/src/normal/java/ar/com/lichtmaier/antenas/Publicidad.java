package ar.com.lichtmaier.antenas;

import android.location.Location;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

class Publicidad
{
	private AdView adView;

	Publicidad(AntenaActivity act)
	{
		adView = new AdView(act);
		adView.setAdUnitId("ca-app-pub-0461170458442008/6164714153");
		adView.setAdSize(AdSize.BANNER);
		ViewGroup v = (ViewGroup)act.findViewById(R.id.principal);
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				(int)(50 * act.getResources().getDisplayMetrics().density), 0);
		v.addView(adView, params);
	}

	void load(Location loc)
	{
		AdRequest.Builder builder = crearAdRequestBuilder();
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
		adView.pause();
	}

	void onResume()
	{
		adView.resume();
	}

	void onDestroy()
	{
		adView.destroy();
	}
}
