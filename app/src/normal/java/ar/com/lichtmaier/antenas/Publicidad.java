package ar.com.lichtmaier.antenas;

import android.location.Location;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import org.gavaghan.geodesy.GlobalCoordinates;

class Publicidad
{
	private final AntenaActivity act;
	private AdView adView;

	Publicidad(AntenaActivity act)
	{
		this.act = act;
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
		AdRequest.Builder builder = new AdRequest.Builder();
		builder.addTestDevice("C34A7B13BA1B16DE0CABA7247F94C289");
		if(loc != null)
			builder.setLocation(loc);
		adView.loadAd(builder.build());
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
