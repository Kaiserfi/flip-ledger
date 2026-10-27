package com.flipledger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What can be made from what, bundled with the plugin (taken from the OSRS Wiki's recipe data,
 * only recipes where every input and the output trade on the GE). No network requests.
 */
final class Recipes
{
	static class Recipe
	{
		/** Output item, how many one action makes. */
		int o;
		double q;
		/** Inputs: [itemId, quantity] per action. */
		double[][] m;
		/** Skill requirements: [name, level]. */
		Object[][] s;
		/** Where it's made, e.g. "Furnace" (may be empty). */
		String f;

		double qtyOf(int itemId)
		{
			for (double[] x : m)
			{
				if ((int) x[0] == itemId)
				{
					return x[1];
				}
			}
			return 0;
		}
	}

	private final Map<Integer, List<Recipe>> byOutput = new HashMap<>();
	private final Map<Integer, List<Recipe>> byInput = new HashMap<>();

	Recipes(Gson gson)
	{
		List<Recipe> all = Collections.emptyList();
		try (InputStream in = Recipes.class.getResourceAsStream("recipes.json"))
		{
			if (in != null)
			{
				all = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
					new TypeToken<List<Recipe>>()
					{
					}.getType());
			}
		}
		catch (Exception ignored)
		{
			// No recipes: processing tips and cost carry-over are simply off
		}
		for (Recipe r : all)
		{
			byOutput.computeIfAbsent(r.o, k -> new ArrayList<>()).add(r);
			for (double[] x : r.m)
			{
				byInput.computeIfAbsent((int) x[0], k -> new ArrayList<>()).add(r);
			}
		}
	}

	List<Recipe> making(int outputId)
	{
		return byOutput.getOrDefault(outputId, Collections.emptyList());
	}

	List<Recipe> using(int inputId)
	{
		return byInput.getOrDefault(inputId, Collections.emptyList());
	}
}
