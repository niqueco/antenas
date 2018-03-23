package ar.com.lichtmaier.antenas;

import org.gavaghan.geodesy.GlobalCoordinates;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CantidadDeAntenasEnRadio
{
	private final TreeMap<Integer, Integer> distanciaACantidadDeAntenas;

	public CantidadDeAntenasEnRadio(List<Antena> antenas, GlobalCoordinates coords)
	{
		distanciaACantidadDeAntenas = new TreeMap<>();
		int acum = 0;
		for(Antena a : antenas)
			distanciaACantidadDeAntenas.put((int)a.distanceTo(coords), ++acum);
	}

	public int en(int m)
	{
		Map.Entry<Integer, Integer> e = distanciaACantidadDeAntenas.floorEntry(m);
		return e.getValue();
	}
}
