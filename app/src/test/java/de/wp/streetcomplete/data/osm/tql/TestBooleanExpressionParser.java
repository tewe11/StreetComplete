package de.wp.streetcomplete.data.osm.tql;

import java.util.Locale;

import de.westnordost.streetcomplete.data.osm.tql.BooleanExpression;
import de.westnordost.streetcomplete.data.osm.tql.BooleanExpressionBuilder;
import de.westnordost.streetcomplete.data.osm.tql.BooleanExpressionValue;
import de.westnordost.streetcomplete.data.osm.tql.StringWithCursor;

public class TestBooleanExpressionParser
{
	public static BooleanExpression<BooleanExpressionValue> parse(String input)
	{
		BooleanExpressionBuilder<BooleanExpressionValue> builder = new BooleanExpressionBuilder<>();
		StringWithCursor reader = new StringWithCursor(input, Locale.US);
		while(!reader.isAtEnd())
		{
			if(reader.nextIsAndAdvance('*')) builder.addAnd();
			else if(reader.nextIsAndAdvance('+')) builder.addOr();
			else if(reader.nextIsAndAdvance('(')) builder.addOpenBracket();
			else if(reader.nextIsAndAdvance(')')) builder.addCloseBracket();
			else builder.addValue(new TestBooleanExpressionValue(String.valueOf(reader.advance())));
		}
		return builder.getResult();
	}
}
