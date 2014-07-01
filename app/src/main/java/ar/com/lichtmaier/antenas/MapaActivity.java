package ar.com.lichtmaier.antenas;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.*;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.*;

public class MapaActivity extends ActionBarActivity
{
	private static BitmapDescriptor íconoAntenita;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_mapa);

		if(savedInstanceState == null)
		{
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new MapaFragment())
					.commit();
		}
	}

	/*@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.mapa, menu);
		return true;
	}*/

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if(id == R.id.action_settings)
		{
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class MapaFragment extends Fragment
	{
		private GoogleMap mapa;

		public MapaFragment()
		{
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
		{
			View rootView = inflater.inflate(R.layout.fragment_mapa, container, false);
			return rootView;
		}

		@Override
		public void onActivityCreated(Bundle savedInstanceState)
		{
			super.onActivityCreated(savedInstanceState);
			FragmentActivity act = getActivity();
			mapa = ((SupportMapFragment)act.getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
			if(mapa == null)
			{
				Toast.makeText(act, "Uhm... por alguna razón no se pudo inicializar el mapita.", Toast.LENGTH_SHORT).show();
				act.finish();
				return;
			}
			mapa.setMyLocationEnabled(true);
			mapa.moveCamera(CameraUpdateFactory.zoomTo(10));
			if(AntenaActivity.coordsUsuario != null)
				mapa.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(AntenaActivity.coordsUsuario.getLatitude(), AntenaActivity.coordsUsuario.getLongitude())));
			if(íconoAntenita == null)
				íconoAntenita = BitmapDescriptorFactory.fromResource(R.drawable.antena);
			for(Antena antena : Antena.dameAntenas(act))
				mapa.addMarker(new MarkerOptions().position(antena.getLatLng()).title(antena.toString()).icon(íconoAntenita));
			act.findViewById(R.id.map).post(new Runnable() {
				@Override
				public void run()
				{
					ActionBarActivity activity = (ActionBarActivity)getActivity();
					if(activity == null)
						return;
					ActionBar actionBar = activity.getSupportActionBar();
					if(actionBar == null)
						return;
					int height = actionBar.getHeight();
					mapa.setPadding(0, height, 0, 0);
				}
			});
		}
	}
}
