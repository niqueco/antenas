package ar.com.lichtmaier.antenas;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

public class MapaActivity extends AppCompatActivity
{
	private long comienzoUsoPantalla;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_mapa);
		Toolbar tb = (Toolbar)findViewById(R.id.toolbar);
		if(tb != null)
		{
			setSupportActionBar(tb);
			//noinspection ConstantConditions
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		}

		if(savedInstanceState == null)
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new MapaFragment())
					.commit();
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		((Aplicacion)getApplication()).reportActivityStart(this);
	}

	@Override
	protected void onStop()
	{
		((Aplicacion)getApplication()).reportActivityStop(this);
		super.onStop();
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
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		int id = item.getItemId();
		if(id == R.id.action_settings)
		{
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

}
