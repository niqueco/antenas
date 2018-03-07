package ar.com.lichtmaier.antenas;

import android.Manifest;
import android.arch.lifecycle.ViewModelProviders;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.gavaghan.geodesy.GlobalCoordinates;

import java.lang.ref.WeakReference;
import java.util.List;

public class TVActivity extends FragmentActivity
{
	private static final int PEDIDO_DE_PERMISO_FINE_LOCATION = 131;

	private AntenasAdapter antenasAdapter;
	private SharedPreferences prefs;
	private AntenasViewModel viewModel;

	private final AntenasAdapter.Callback antenasAdapterListener = new AntenasAdapter.Callback()
	{
		@Override
		public void onAntenaClicked(Antena antena, View view)
		{

		}

		@Override
		public void onAdapterReady()
		{
			terminarDeConfigurar();
		}

		@Override
		public void onAntenasActualizadas(List<AntenasAdapter.AntenaListada> antenasListadas)
		{
			antenasActualizadas(antenasListadas);
		}
	};

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		viewModel = ViewModelProviders.of(this).get(AntenasViewModel.class);
		viewModel.init(false);

		setContentView(R.layout.tv_activity);

		ProgressBar pb = findViewById(R.id.progressBar);
		if(pb != null)
		{
			prenderAnimación = new PrenderAnimación(pb);
			pb.postDelayed(prenderAnimación, 400);
			pb.postDelayed(avisoDemora = new AvisoDemora(this), 15000);
		}

		PreferenceManager.setDefaultValues(this, R.xml.prefs, false);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		final RecyclerView rv = findViewById(R.id.antenas);

		if(rv != null)
		{
			antenasAdapter = new AntenasAdapter(this, null, antenasAdapterListener, R.layout.antena_tv, getLifecycle());
			rv.setAdapter(antenasAdapter);
		}

		if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
		{
			ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, PEDIDO_DE_PERMISO_FINE_LOCATION);
		} else
		{
			crearLocationLiveData();
		}
	}

	private void crearLocationLiveData()
	{
		viewModel.locationLiveData.inicializarConPermiso(this);
		viewModel.locationLiveData.observe(this, this::nuevaUbicación);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		if(requestCode == PEDIDO_DE_PERMISO_FINE_LOCATION)
		{
			if(grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
				//noinspection ResourceType
				crearLocationLiveData();
			else
				finish();
		} else
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	@Override
	protected void onDestroy()
	{
		if(antenasAdapter != null)
			antenasAdapter.onDestroy();
		super.onDestroy();
	}

	private void nuevaUbicación(Location location)
	{
		antenasAdapter.nuevaUbicación(new GlobalCoordinates(location.getLatitude(), location.getLongitude()));
	}

	/** Se llama cuando antenasAdapter avisa que ya está toda la información. */
	private void terminarDeConfigurar()
	{
		final ProgressBar pb = findViewById(R.id.progressBar);
		if(pb != null)
		{
			if(prenderAnimación.comienzoAnimación != -1)
			{
				long falta = 600 - (System.currentTimeMillis() - prenderAnimación.comienzoAnimación);
				if(falta <= 0)
					pb.setVisibility(View.GONE);
				else
					pb.postDelayed(() -> pb.setVisibility(View.GONE), falta);
			} else
			{
				prenderAnimación.cancelado = true;
				pb.removeCallbacks(prenderAnimación);
			}
			prenderAnimación = null;

			avisoDemora.cancelado = true;
			pb.removeCallbacks(avisoDemora);
			avisoDemora = null;
		}
	}

	private void antenasActualizadas(List<AntenasAdapter.AntenaListada> antenasListadas)
	{
		int maxDist = Integer.parseInt(prefs.getString("max_dist", "60")) * 1000;
		TextView problema = findViewById(R.id.problema);
		if(antenasListadas.isEmpty())
		{
			((ViewGroup.MarginLayoutParams)problema.getLayoutParams()).topMargin = 0;
			//StringBuilder sb = new StringBuilder(getString(R.string.no_se_encontraron_antenas, Formatos.formatDistance(this, maxDist)));
			//String[] vv = getResources().getStringArray(R.array.pref_max_dist_values);
			//if(Integer.parseInt(vv[vv.length-1]) * 1000 != maxDist)
			//	sb.append(' ').append(getString(R.string.podes_incrementar_radio));
			//String message = sb.toString();
			String message = getString(R.string.no_se_encontraron_antenas, Formatos.formatDistance(this, maxDist));
			problema.setText(message);
			problema.setVisibility(View.VISIBLE);
		} else
		{
			problema.setVisibility(View.GONE);
		}
	}

	private PrenderAnimación prenderAnimación;

	private static class PrenderAnimación implements Runnable
	{
		private final View pb;
		long comienzoAnimación = -1;
		boolean cancelado = false;

		PrenderAnimación(View pb)
		{
			this.pb = pb;
		}

		@Override
		public void run()
		{
			if(cancelado)
				return;
			pb.setVisibility(View.VISIBLE);
			comienzoAnimación = System.currentTimeMillis();
		}
	}

	private AvisoDemora avisoDemora;

	private static class AvisoDemora implements Runnable
	{
		private final WeakReference<TVActivity> actRef;
		boolean cancelado = false;

		private AvisoDemora(TVActivity act)
		{
			this.actRef = new WeakReference<>(act);
		}

		@Override
		public void run()
		{
			if(cancelado)
				return;
			TVActivity act = actRef.get();
			if(act == null)
				return;

			TextView problemaView = act.findViewById(R.id.problema);
			problemaView.setText(R.string.aviso_demora_ubicación);
			problemaView.setVisibility(View.VISIBLE);
			((ViewGroup.MarginLayoutParams)problemaView.getLayoutParams()).topMargin = (int)(48 * act.getResources().getDisplayMetrics().density);
		}
	}
}
