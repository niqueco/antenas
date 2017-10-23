package ar.com.lichtmaier.antenas;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.view.MenuItem;

import ar.com.lichtmaier.util.AppCompatPreferenceActivity;

public class PreferenciasActivity extends AppCompatPreferenceActivity
{
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setupActionBar();
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupActionBar()
	{
		ActionBar actionBar = getActionBar();
		if(actionBar != null)
			actionBar.setDisplayHomeAsUpEnabled(true);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		int id = item.getItemId();
		if(id == android.R.id.home)
		{
			NavUtils.navigateUpFromSameTask(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState)
	{
		super.onPostCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.prefs);
		bindPreferenceSummaryToValue(findPreference("max_dist"));
		bindPreferenceSummaryToValue(findPreference("unit"));
		for(País país : País.values())
		{
			int nombre;
			switch(país)
			{
				case AR:
					nombre = R.string.pref_AR;
					break;
				case AT:
					nombre = R.string.pref_AT;
					break;
				case AU:
					nombre = R.string.pref_AU;
					break;
				case BR:
					nombre = R.string.pref_BR;
					break;
				case CA:
					nombre = R.string.pref_CA;
					break;
				case CO:
					nombre = R.string.pref_CO;
					break;
				case NZ:
					nombre = R.string.pref_NZ;
					break;
				case PT:
					nombre = R.string.pref_PT;
					break;
				case UK:
					nombre = R.string.pref_UK;
					break;
				case US:
					nombre = R.string.pref_US;
					break;
				case UY:
					nombre = R.string.pref_UY;
					break;
				default:
					throw new RuntimeException("pais " + país);
			}
			int cantAntenas = Antena.dameAntenas(this, país).size();
			findPreference("mapa_país_" + país)
				.setSummary(getResources().getQuantityString(R.plurals.pref_país_summary,
					cantAntenas, cantAntenas,
					getString(nombre)));
		}

	}

	/**
	 * A preference value change listener that updates the preference's
	 * summary to reflect its new value.
	 */
	private static final class BindPreferenceSummaryToValueListener implements
			Preference.OnPreferenceChangeListener
	{
		@Override
		public boolean onPreferenceChange(Preference preference, Object value)
		{
			String stringValue = value.toString();

			if(preference instanceof ListPreference)
			{
				ListPreference listPreference = (ListPreference)preference;
				int index = listPreference.findIndexOfValue(stringValue);
				preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);
			} else
			{
				preference.setSummary(stringValue);
			}
			return true;
		}
	}
	private final static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new BindPreferenceSummaryToValueListener();

	private static void bindPreferenceSummaryToValue(Preference preference)
	{
		preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
		sBindPreferenceSummaryToValueListener.onPreferenceChange(
				preference,
				PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(
						preference.getKey(), ""));
	}
}
