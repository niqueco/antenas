package ar.com.lichtmaier.antenas;

import android.content.Context;
import android.support.v7.widget.AppCompatTextView;
import android.text.TextUtils;
import android.util.AttributeSet;

public class CommaEllipsizeTextView extends AppCompatTextView
{
	private CharSequence original;
	boolean nosotros = false;

	public CommaEllipsizeTextView(Context context)
	{
		super(context);
	}

	public CommaEllipsizeTextView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	public CommaEllipsizeTextView(Context context, AttributeSet attrs, int defStyleAttr)
	{
		super(context, attrs, defStyleAttr);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		super.onSizeChanged(w, h, oldw, oldh);

		CharSequence text;
		if(original == null)
			original = text = getText();
		else
			text = original;
		final CharSequence newText = TextUtils.commaEllipsize(text, getPaint(), w * 3, getContext().getString(R.string.one_more), getContext().getString(R.string.some_more));
		if(!equals(getText(), newText))
		{
			nosotros = true;
			try  {
				setText(newText);
			} finally  {
				nosotros = false;
			}
			if(pedirLayout == null)
				pedirLayout = new PedirLayout();
			post(pedirLayout);
		}
	}

	private static boolean equals(Object a, Object b) {
		return (a == null) ? (b == null) : a.equals(b);
	}

	@Override
	protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter)
	{
		super.onTextChanged(text, start, lengthBefore, lengthAfter);

		if(!nosotros)
			original = null;
	}

	private class PedirLayout implements Runnable
	{
		@Override
		public void run()
		{
			requestLayout();
		}
	}
	private Runnable pedirLayout;
}
