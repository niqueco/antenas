package ar.com.lichtmaier.antenas;

import android.content.Context;
import android.os.Build;
import android.support.v7.widget.AppCompatTextView;
import android.text.TextUtils;
import android.util.AttributeSet;

import java.util.List;

import ar.com.lichtmaier.util.StringUtils;

public class CommaEllipsizeTextView extends AppCompatTextView
{
	private boolean nosotros = false;
	private List<CharSequence> items;

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

	void setItems(List<CharSequence> items)
	{
		if((items == this.items) || (items != null && items.equals(this.items)))
			return;
		this.items = items;
		requestLayout();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		if(items == null)
			return;

		Context context = getContext();
		int w = getMeasuredWidth();

		if(w == 0)
			return;

		int avail = w * context.getResources().getInteger(R.integer.lineas_resumen);

		CharSequence newText;
		String separator = context.getString(R.string.separator);
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			newText = TextUtils.listEllipsize(context, items, separator, getPaint(), avail, R.plurals.more);
		else
			newText = TextUtils.commaEllipsize(StringUtils.join(items, separator), getPaint(), avail,
					context.getResources().getQuantityString(R.plurals.more, 1),
					context.getResources().getQuantityString(R.plurals.more, 10));

		nosotros = true;
		try  {
			setText(newText);
		} finally  {
			nosotros = false;
		}

		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	@Override
	public void setText(CharSequence text, BufferType type)
	{
		super.setText(text, type);
		if(!nosotros)
			items = null;
	}
}
