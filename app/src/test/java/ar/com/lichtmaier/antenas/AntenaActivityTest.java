package ar.com.lichtmaier.antenas;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class AntenaActivityTest
{
	@Test
	public void actualizarPreferenciaDistanciaMáxima()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.application);
		prefs.edit().putString("max_dist","80").apply();
		AntenaActivity.actualizarPreferenciaDistanciaMáxima(prefs);
		assertEquals(80000, prefs.getInt("max_dist", -1));
	}
}