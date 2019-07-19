package de.wp.streetcomplete.data.meta;

import android.content.res.AssetManager;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.westnordost.backbiking.data.meta.CountryInfo;
import de.westnordost.backbiking.data.meta.CountryInfos;
import de.westnordost.backbiking.quests.opening_hours.model.Weekdays;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.*;

public class CountryInfosTest
{
	private void checkFirstDayOfWorkweekIsValid(CountryInfo info)
	{
		assertNotNull(info.getFirstDayOfWorkweek());
		assertTrue(Weekdays.Companion.getWeekdayIndex(info.getFirstDayOfWorkweek()) > -1);
	}

	private void checkMeasurementUnitIsEitherMetricOrImperial(CountryInfo info)
	{
		assertNotNull(info.getMeasurementSystem());
		assertTrue(info.getMeasurementSystem().contains("metric") || info.getMeasurementSystem().contains("imperial"));
	}

	private void checkAdditionalValidHousenumberRegexes(Map<String, CountryInfo> infos)
	{
		assertTrue("99 bis".matches(infos.get("FR").getAdditionalValidHousenumberRegex()));
		assertTrue("s/n".matches(infos.get("ES").getAdditionalValidHousenumberRegex()));
	}

	private void checkRegularShoppingDaysIsBetween0And7(CountryInfo info)
	{
		assertNotNull(info.getRegularShoppingDays());
		assertTrue(info.getRegularShoppingDays() <= 7);
		assertTrue(info.getRegularShoppingDays() >= 0);
	}

	private static List<String> validWeekdays = Arrays.asList("Mo","Tu","We","Th","Fr","Sa","Su");
	private void checkStartOfWorkweekValid(CountryInfo info)
	{
		assertTrue(validWeekdays.contains(info.getFirstDayOfWorkweek()));
	}

	private void checkForEach(CountryInfo info)
	{
		checkFirstDayOfWorkweekIsValid(info);
		checkMeasurementUnitIsEitherMetricOrImperial(info);
		checkRegularShoppingDaysIsBetween0And7(info);
		checkStartOfWorkweekValid(info);
	}

	@Test public void all() throws IOException
	{
		Map<String, CountryInfo> infos = getAllCountryInfos();
		for(Map.Entry<String,CountryInfo> elem : infos.entrySet())
		{
			try
			{
				CountryInfo ci = elem.getValue();
				assertEquals(elem.getKey().split("-")[0], ci.countryCode);
				checkForEach(ci);
			}
			catch (Throwable e)
			{
				throw new RuntimeException("Error for "+ elem.getKey(), e);
			}
		}

		checkAdditionalValidHousenumberRegexes(infos);
	}

	private Map<String, CountryInfo> getAllCountryInfos() throws IOException
	{
		AssetManager am = getInstrumentation().getTargetContext().getAssets();
		String[] fileList = am.list("country_metadata");
		assertNotNull(fileList);
		CountryInfos cis = new CountryInfos(am, null);
		Map<String,CountryInfo> all = new HashMap<>();
		for (String filename : fileList)
		{
			String country = filename.substring(0, filename.lastIndexOf("."));
			try
			{
				CountryInfo info = cis.get(Collections.singletonList(country));
				all.put(country, info);
			}
			catch (Throwable e)
			{
				throw new RuntimeException("Error for " + filename, e);
			}
		}
		return all;
	}
}