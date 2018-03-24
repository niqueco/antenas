package ar.com.lichtmaier.antenas;

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
}