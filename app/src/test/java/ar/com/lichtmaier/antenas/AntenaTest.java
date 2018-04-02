package ar.com.lichtmaier.antenas;

import android.location.Location;

import org.gavaghan.geodesy.GlobalCoordinates;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class AntenaTest
{
	@Test
	public void antenasEnRectángulo()
	{
		List<Antena> antenas = new ArrayList<>();
		Antena.antenasEnRectángulo(RuntimeEnvironment.application, -34.559414, -58.459576, -34.631529, -58.372368, antenas);
		assertEquals(2, antenas.size());
	}

	@Test
	public void rumboDesde()
	{
		Antena antena = Antena.dameAntena(RuntimeEnvironment.application, País.AR, 1);

		Location casa = new Location("*");
		casa.setLatitude(-34.595479);
		casa.setLongitude(-58.415715);

		Location antenaLoc = new Location("*");
		antenaLoc.setLatitude(antena.getLatLng().latitude);
		antenaLoc.setLongitude(antena.getLatLng().longitude);

		float z = casa.bearingTo(antenaLoc);
		if(z < 0)
			z += 360;
		assertEquals(z, antena.rumboDesde(new GlobalCoordinates(-34.595479, -58.415715)), .0001);
		assertEquals(casa.distanceTo(antenaLoc), antena.distanceTo(new GlobalCoordinates(-34.595479, -58.415715)), 1);
	}
}