package ar.com.lichtmaier.antenas;

import android.content.Context;
import android.support.v7.widget.AppCompatTextView;
import android.text.TextUtils;
import android.util.AttributeSet;

public class CommaEllipsizeTextView extends AppCompatTextView
{
	private CharSequence original;
	private boolean nosotros = false;

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
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		CharSequence text;
		if(original == null)
			original = text = getText();
		else
			text = original;
		final CharSequence newText = TextUtils.commaEllipsize(text, getPaint(), getMeasuredWidth() * getContext().getResources().getInteger(R.integer.lineas_resumen), getContext().getString(R.string.one_more), getContext().getString(R.string.some_more));
		if(!equals(getText(), newText))
		{
			nosotros = true;
			try  {
				setText(newText);
			} finally  {
				nosotros = false;
			}
		}

		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
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
}
