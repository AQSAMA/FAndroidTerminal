// SPDX-License-Identifier: GPL-3.0-only
package io.github.aqsama.fandroidterminal

import io.github.aqsama.fandroidterminal.runtime.ShellRecipe
import org.json.JSONArray
import org.json.JSONObject

internal fun decodeRecipes(encoded: String): List<ShellRecipe> {
    val list = JSONArray(encoded)
    require(list.length() <= 40)
    val recipes = List(list.length()) { index ->
        val item = list.getJSONObject(index)
        val inputs = item.getJSONArray("inputs")
        require(inputs.length() <= 3)
        ShellRecipe(item.getString("id"), item.getString("name"), item.getString("script"),
            List(inputs.length()) { inputs.getString(it) }).validated()
    }
    require(recipes.distinctBy { it.id }.size == recipes.size)
    return recipes
}

internal fun encodeRecipes(recipes: List<ShellRecipe>): String {
    require(recipes.size <= 40 && recipes.distinctBy { it.id }.size == recipes.size)
    return JSONArray().apply {
        recipes.forEach { recipe ->
            recipe.validated()
            put(JSONObject().put("id", recipe.id).put("name", recipe.name).put("script", recipe.script)
                .put("inputs", JSONArray(recipe.inputs)))
        }
    }.toString()
}
