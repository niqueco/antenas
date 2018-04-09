package ar.com.lichtmaier.antenas;

import android.Manifest;
import android.arch.lifecycle.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.location.LocationRequest;

import ar.com.lichtmaier.antenas.location.LocationLiveData;
import ar.com.lichtmaier.antenas.location.TieneLocation;

public class MapaActivity extends AppCompatActivity implements TieneLocation
{
	private static final int PEDIDO_DE_PERMISO_ACCESS_FINE_LOCATION = 11112;

	private long comienzoUsoPantalla;
	private LocationLiveData realLocation;
	private LiveData<Location> location;

	private AyudanteDePagos ayudanteDePagos;
	private MenuItem opciónPagar;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		Antena.applicationContext = getApplicationContext();
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_mapa);
		Antena.applicationContext = null;
		Toolbar tb = findViewById(R.id.toolbar);
		if(tb != null)
		{
			setSupportActionBar(tb);
			//noinspection ConstantConditions
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}

		ayudanteDePagos = AyudanteDePagos.dameInstancia(this);
		ayudanteDePagos.observe(this, (pro) -> {
			if(pro == null)
				return;
			if(opciónPagar != null)
				opciónPagar.setVisible(!pro);
		});

		if(savedInstanceState == null)
		{
			if(getIntent().getExtras() == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
			{
				ShortcutManager sm = getSystemService(ShortcutManager.class);
				if(sm != null)
					sm.reportShortcutUsed("mapa");
			}
		}

		realLocation = LocationLiveData.create(this, LocationRequest.create()
				.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
				.setInterval(200)
				.setFastestInterval(200)
				.setSmallestDisplacement(1), Float.MAX_VALUE);

		location = Lugar.dameUbicaciónFusionada(realLocation);

		if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
		{
			realLocation.inicializarConPermiso(this);
		} else
			ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PEDIDO_DE_PERMISO_ACCESS_FINE_LOCATION);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		if(requestCode == PEDIDO_DE_PERMISO_ACCESS_FINE_LOCATION)
		{
			if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
				realLocation.inicializarConPermiso(this);
		} else
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		comienzoUsoPantalla = System.currentTimeMillis();
	}

	@Override
	protected void onPause()
	{
		if(System.currentTimeMillis() - comienzoUsoPantalla > 1000 * 10)
			Calificame.registráQueMiróElMapa(this);
		super.onPause();
	}

	@Override
	public void onBackPressed()
	{
		// Si fuimos llamados sólo para mostrar una antena, al primer "back" nos vamos.
		if(((MapaFragment)getSupportFragmentManager().findFragmentById(R.id.container)).modoMostrarAntena)
		{
			finish();
			return;
		}

		FragmentManager fm = getSupportFragmentManager();
		if(fm.getBackStackEntryCount() == 0)
		{
			super.onBackPressed();
			return;
		}
		fm.popBackStack("canales", FragmentManager.POP_BACK_STACK_INCLUSIVE);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.mapa, menu);
		opciónPagar = menu.findItem(R.id.action_pagar);
		Boolean pro = ayudanteDePagos.getValue();
		if(pro != null)
			opciónPagar.setVisible(!pro);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		int id = item.getItemId();
		switch(id)
		{
			case R.id.action_pagar:
				ayudanteDePagos.pagar(this);
				return true;
			case R.id.action_settings:
				Intent i = new Intent(this, PreferenciasActivity.class);
				startActivity(i);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public void canalSeleccionado(Antena antena, Canal canal)
	{
		MapaFragment mfr = (MapaFragment)getSupportFragmentManager().findFragmentById(R.id.container);
		mfr.canalSeleccionado(antena, canal);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if(realLocation.onActivityResult(requestCode, resultCode, data))
			return;
		if(ayudanteDePagos.onActivityResult(requestCode, resultCode, data))
			return;
		super.onActivityResult(requestCode, resultCode, data);
	}

	@NonNull
	public LiveData<Location> getLocation()
	{
		return location;
	}
}
