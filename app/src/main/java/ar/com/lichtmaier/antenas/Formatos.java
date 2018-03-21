package ar.com.lichtmaier.antenas;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import java.text.NumberFormat;
import java.util.Locale;

public class Formatos
{
	public static String formatDistance(Context context, double distancia)
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String unit = prefs.getString("unit", "km");
		return format(distancia / unitFactor(unit)) + ' ' + unit;
	}

	public static double unitFactor(String unit)
	{
		switch(unit)
		{
			case "km":
				return 1000.0;
			case "mi":
				return 1609.344;
			default:
				throw new RuntimeException("unit: " + unit);
		}
	}

	public static String formatPower(float potencia)
	{
		return format(potencia) + " kW";
	}

	private static String format(double valor)
	{
		NumberFormat nf = getNumberFormat();
		nf.setMaximumFractionDigits(valor < 1 ? 2 : 1);
		return nf.format(valor);
	}

	private static NumberFormat cachedNumberFormat;
	private static Locale cachedNumberFormatLocale;

	private static NumberFormat getNumberFormat()
	{
		Locale defaultLocale = Locale.getDefault();
		if(!defaultLocale.equals(cachedNumberFormatLocale) || cachedNumberFormat == null)
		{
			Locale locale = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
					? defaultLocale
					: "es".equals(defaultLocale.getLanguage())
						? new Locale("es", "AR")
						: defaultLocale;
			cachedNumberFormat = NumberFormat.getInstance(locale);
			cachedNumberFormatLocale = defaultLocale;
		}
		return cachedNumberFormat;
	}
}
