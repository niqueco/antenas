package ar.com.lichtmaier.antenas;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.view.MenuItem;

import ar.com.lichtmaier.util.AppCompatPreferenceActivity;
import ar.com.lichtmaier.util.DistanceSliderPreference;

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
			CharSequence summary;

			if(preference instanceof ListPreference)
			{
				ListPreference listPreference = (ListPreference)preference;
				int index = listPreference.findIndexOfValue(value.toString());
				summary = index >= 0 ? listPreference.getEntries()[index] : null;
			} else if(preference instanceof DistanceSliderPreference)
			{
				summary = Formatos.formatDistance(preference.getContext(), ((Integer)value).doubleValue());
			} else
			{
				summary = value.toString();
			}
			preference.setSummary(summary);
			return true;
		}
	}
	private final static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new BindPreferenceSummaryToValueListener();

	private static void bindPreferenceSummaryToValue(Preference preference)
	{
		preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(preference.getContext());
		Object value;
		if(preference instanceof DistanceSliderPreference)
			value = prefs.getInt(preference.getKey(), 60000);
		else
			value = prefs.getString(preference.getKey(), "");
		sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, value);
	}
}
