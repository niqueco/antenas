package ar.com.lichtmaier.util;

import android.support.annotation.NonNull;
import android.text.SpannableStringBuilder;

import java.util.List;

public class StringUtils
{
	@NonNull
	public static SpannableStringBuilder join(List<CharSequence> l, CharSequence delimiter)
	{
		SpannableStringBuilder sb = new SpannableStringBuilder();
		boolean primero = true;
		for(CharSequence c : l)
		{
			if(primero)
				primero = false;
			else
				sb.append(delimiter);
			sb.append(c);
		}
		return sb;
	}
}
