package ar.com.lichtmaier.antenas;

import org.gavaghan.geodesy.GlobalCoordinates;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.List;

import ar.com.lichtmaier.util.AsyncLiveData;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class CantidadDeAntenasEnRadioTest
{
	private static final GlobalCoordinates COLUMBUS_CIRCLE = new GlobalCoordinates(40.767982, -73.981893);

	@Test
	public void en() throws Exception
	{
		AsyncLiveData<List<Antena>> ld = (AsyncLiveData<List<Antena>>)Antena.dameAntenasCerca(RuntimeEnvironment.application, COLUMBUS_CIRCLE, 100000, false);
		List<Antena> antenas = ld.getFuture().get();
		CantidadDeAntenasEnRadio c = new CantidadDeAntenasEnRadio(antenas, COLUMBUS_CIRCLE);
		assertEquals(0, c.en(10));
		assertEquals(2, c.en(1500));
		assertEquals(antenas.size(),c.en(Integer.MAX_VALUE));
	}
}