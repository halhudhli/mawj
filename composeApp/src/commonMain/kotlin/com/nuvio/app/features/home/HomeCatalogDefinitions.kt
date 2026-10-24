package com.nuvio.app.features.home

import com.nuvio.app.core.i18n.localizedMediaTypeLabel
import com.nuvio.app.features.addons.AddonCatalog
import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.catalog.supportsPagination
import com.nuvio.app.features.servers.ServerCatalog
import kotlinx.coroutines.runBlocking
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.home_catalog_default_title
import org.jetbrains.compose.resources.getString

data class HomeCatalogDefinition(
    val key: String,
    val defaultTitle: String,
    val catalogName: String,
    val addonName: String,
    val manifestUrl: String,
    val type: String,
    val catalogId: String,
    val supportsPagination: Boolean,
    val descriptorSignature: String,
    val serverConnectionId: String? = null,
) {
    val cacheKey: String
        get() = "$key|$descriptorSignature"

    fun titleFor(showCatalogType: Boolean): String =
        translateCatalogTitleAr(if (showCatalogType) defaultTitle else catalogName)
}

fun buildAddonCatalogRefreshSignature(addons: List<ManagedAddon>): List<String> =
    addons.enabledAddons().map { addon ->
        val signature = CatalogDescriptorSignature()
        signature.addAddon(addon)
        addon.manifest?.let { manifest ->
            signature.addManifest(manifest)
            manifest.catalogs.forEach(signature::addCatalog)
        }
        signature.value()
    }.sorted()

fun buildHomeCatalogDefinitions(addons: List<ManagedAddon>): List<HomeCatalogDefinition> =
    addons.enabledAddons().mapNotNull { addon ->
        val manifest = addon.manifest ?: return@mapNotNull null
        addon to manifest
    }.flatMap { (addon, manifest) ->
        manifest.catalogs
            .filter { catalog -> catalog.extra.none { it.isRequired } }
            .map { catalog ->
                HomeCatalogDefinition(
                    key = "${manifest.id}:${catalog.type}:${catalog.id}",
                    defaultTitle = runBlocking {
                        getString(
                            Res.string.home_catalog_default_title,
                            translateCatalogNameHarbor(catalog.name),
                            localizedMediaTypeLabel(catalog.type),
                        )
                    },
                    catalogName = translateCatalogNameHarbor(catalog.name),
                    addonName = addon.displayTitle,
                    manifestUrl = addon.manifestUrl,
                    type = catalog.type,
                    catalogId = catalog.id,
                    supportsPagination = catalog.supportsPagination(),
                    descriptorSignature = buildHomeCatalogDescriptorSignature(addon, manifest, catalog),
                )
            }
    }.distinctBy { "${it.type}:${it.catalogName.lowercase()}" } + ServerCatalog.homeDefinitions()

private fun buildHomeCatalogDescriptorSignature(
    addon: ManagedAddon,
    manifest: AddonManifest,
    catalog: AddonCatalog,
): String {
    val signature = CatalogDescriptorSignature()
    signature.addAddon(addon)
    signature.addManifest(manifest)
    signature.addCatalog(catalog)
    return signature.value()
}

private class CatalogDescriptorSignature {
    private var hash = -3750763034362895579L

    fun add(value: String?) {
        val text = value.orEmpty()
        mix(text.length)
        text.forEach { character -> mix(character.code) }
    }

    fun add(value: Boolean) {
        mix(if (value) 1 else 0)
    }

    fun add(value: Int?) {
        mix(value ?: Int.MIN_VALUE)
    }

    fun addAddon(addon: ManagedAddon) {
        add(addon.userSetName)
        add(addon.enabled)
        add(addon.isRefreshing)
        add(addon.errorMessage)
        add(addon.manifestUrl)
    }

    fun addManifest(manifest: AddonManifest) {
        add(manifest.id)
        add(manifest.name)
        add(manifest.version)
        add(manifest.description)
        add(manifest.logoUrl)
        add(manifest.transportUrl)
        manifest.types.forEach(::add)
        manifest.idPrefixes.forEach(::add)
        manifest.resources.forEach { resource ->
            add(resource.name)
            resource.types.forEach(::add)
            resource.idPrefixes.forEach(::add)
        }
        add(manifest.behaviorHints.configurable)
        add(manifest.behaviorHints.configurationRequired)
        add(manifest.behaviorHints.adult)
        add(manifest.behaviorHints.p2p)
    }

    fun addCatalog(catalog: AddonCatalog) {
        add(catalog.type)
        add(catalog.id)
        add(catalog.name)
        add(catalog.supportsPagination())
        catalog.extra.forEach { extra ->
            add(extra.name)
            add(extra.isRequired)
            extra.options.forEach(::add)
            add(extra.optionsLimit)
        }
    }

    fun value(): String = hash.toULong().toString(16)

    private fun mix(value: Int) {
        hash = (hash xor value.toLong()) * 1099511628211L
    }
}

internal fun String.displayLabel(): String = localizedMediaTypeLabel(this)

internal fun translateCatalogNameHarbor(rawName: String): String {
    val clean = rawName.trim()
    val map = mapOf(
        "top" to "\u0627\u0644\u0623\u0639\u0644\u0649 \u062a\u0642\u064a\u064a\u0645\u0627\u064b",
        "top rated" to "\u0627\u0644\u0623\u0639\u0644\u0649 \u062a\u0642\u064a\u064a\u0645\u0627\u064b",
        "popular" to "\u0627\u0644\u0623\u0643\u062b\u0631 \u0634\u0639\u0628\u064a\u0629",
        "trending" to "\u0627\u0644\u0631\u0627\u0626\u062c \u0627\u0644\u0622\u0646",
        "featured" to "\u0645\u062e\u062a\u0627\u0631\u0627\u062a \u0645\u0645\u064a\u0632\u0629",
        "for you" to "\u0645\u0642\u062a\u0631\u062d \u0644\u0643",
        "new" to "\u0623\u062d\u062f\u062b \u0627\u0644\u0625\u0635\u062f\u0627\u0631\u0627\u062a",
        "new releases" to "\u0623\u062d\u062f\u062b \u0627\u0644\u0625\u0635\u062f\u0627\u0631\u0627\u062a",
        "latest" to "\u0627\u0644\u0645\u0636\u0627\u0641 \u062d\u062f\u064a\u062b\u0627\u064b",
        "imdbrating" to "\u0627\u0644\u0623\u0639\u0644\u0649 \u062a\u0642\u064a\u064a\u0645\u0627\u064b (IMDb)",
        "year" to "\u062d\u0633\u0628 \u0627\u0644\u0633\u0646\u0629",
        "action" to "\u0623\u0643\u0634\u0646",
        "adventure" to "\u0645\u063a\u0627\u0645\u0631\u0629",
        "animation" to "\u0631\u0633\u0648\u0645 \u0645\u062a\u062d\u0631\u0643\u0629",
        "anime" to "\u0623\u0646\u0645\u064a",
        "comedy" to "\u0643\u0648\u0645\u064a\u062f\u064a\u0627",
        "crime" to "\u062c\u0631\u064a\u0645\u0629",
        "documentary" to "\u0648\u062b\u0627\u0626\u0642\u064a",
        "drama" to "\u062f\u0631\u0627\u0645\u0627",
        "family" to "\u0639\u0627\u0626\u0644\u064a",
        "fantasy" to "\u0641\u0627\u0646\u062a\u0627\u0632\u064a\u0627",
        "history" to "\u062a\u0627\u0631\u064a\u062e\u064a",
        "horror" to "\u0631\u0639\u0628",
        "mystery" to "\u063a\u0645\u0648\u0636",
        "romance" to "\u0631\u0648\u0645\u0627\u0646\u0633\u064a",
        "sci-fi" to "\u062e\u064a\u0627\u0644 \u0639\u0644\u0645\u064a",
        "science fiction" to "\u062e\u064a\u0627\u0644 \u0639\u0644\u0645\u064a",
        "thriller" to "\u0625\u062b\u0627\u0631\u0629 \u0648\u062a\u0634\u0648\u064a\u0642",
        "movies" to "\u0623\u0641\u0644\u0627\u0645",
        "movie" to "\u0623\u0641\u0644\u0627\u0645",
        "series" to "\u0645\u0633\u0644\u0633\u0644\u0627\u062a",
        "tv shows" to "\u0645\u0633\u0644\u0633\u0644\u0627\u062a",
        "tv" to "\u062a\u0644\u0641\u0632\u064a\u0648\u0646",
        "discover" to "\u0627\u0633\u062a\u0643\u0634\u0641",
        "continue watching" to "\u0645\u062a\u0627\u0628\u0639\u0629 \u0627\u0644\u0645\u0634\u0627\u0647\u062f\u0629",
        "recommended" to "\u0645\u0648\u0635\u0649 \u0628\u0647",
        "most watched" to "\u0627\u0644\u0623\u0643\u062b\u0631 \u0645\u0634\u0627\u0647\u062f\u0629",
        "box office" to "\u0634\u0628\u0627\u0643 \u0627\u0644\u062a\u0630\u0627\u0643\u0631",
        "collections" to "\u0627\u0644\u0633\u0644\u0627\u0633\u0644 \u0648\u0627\u0644\u0645\u062c\u0645\u0648\u0639\u0627\u062a",
        "war" to "\u062d\u0631\u0628",
        "western" to "\u063a\u0631\u0628 \u0623\u0645\u0631\u064a\u0643\u064a",
        "biography" to "\u0633\u064a\u0631\u0629 \u0630\u0627\u062a\u064a\u0629",
        "sport" to "\u0631\u064a\u0627\u0636\u0629",
        "music" to "\u0645\u0648\u0633\u064a\u0642\u0649" ,
        "war" to "\u062d\u0631\u0628",
        "western" to "\u063a\u0631\u0628 \u0623\u0645\u0631\u064a\u0643\u064a"
    )
    return map[clean.lowercase()] ?: clean.ifBlank { rawName }
}

internal fun translateCatalogTitleAr(raw: String): String {
    val map = mapOf(
        "for you" to "\u0645\u0642\u062a\u0631\u062d \u0644\u0643",
        "popular" to "\u0627\u0644\u0623\u0643\u062b\u0631 \u0634\u0639\u0628\u064a\u0629",
        "trending" to "\u0627\u0644\u0631\u0627\u0626\u062c \u0627\u0644\u0622\u0646",
        "top" to "\u0627\u0644\u0623\u0639\u0644\u0649 \u062a\u0642\u064a\u064a\u0645\u0627\u064b",
        "top rated" to "\u0627\u0644\u0623\u0639\u0644\u0649 \u062a\u0642\u064a\u064a\u0645\u0627\u064b",
        "featured" to "\u0645\u0645\u064a\u0632",
        "new" to "\u0627\u0644\u0623\u062d\u062f\u062b",
        "new releases" to "\u0623\u062d\u062f\u062b \u0627\u0644\u0625\u0635\u062f\u0627\u0631\u0627\u062a",
        "latest" to "\u0623\u062d\u062f\u062b \u0627\u0644\u0625\u0636\u0627\u0641\u0627\u062a",
        "now playing" to "\u064a\u0639\u0631\u0636 \u0627\u0644\u0622\u0646",
        "upcoming" to "\u0642\u0631\u064a\u0628\u0627\u064b",
        "action" to "\u0623\u0643\u0634\u0646",
        "adventure" to "\u0645\u063a\u0627\u0645\u0631\u0629",
        "animation" to "\u0631\u0633\u0648\u0645 \u0645\u062a\u062d\u0631\u0643\u0629",
        "anime" to "\u0623\u0646\u0645\u064a",
        "comedy" to "\u0643\u0648\u0645\u064a\u062f\u064a\u0627",
        "crime" to "\u062c\u0631\u064a\u0645\u0629",
        "documentary" to "\u0648\u062b\u0627\u0626\u0642\u064a",
        "drama" to "\u062f\u0631\u0627\u0645\u0627",
        "family" to "\u0639\u0627\u0626\u0644\u064a",
        "fantasy" to "\u0641\u0627\u0646\u062a\u0627\u0632\u064a\u0627",
        "history" to "\u062a\u0627\u0631\u064a\u062e\u064a",
        "horror" to "\u0631\u0639\u0628",
        "mystery" to "\u063a\u0645\u0648\u0636",
        "romance" to "\u0631\u0648\u0645\u0627\u0646\u0633\u064a",
        "science fiction" to "\u062e\u064a\u0627\u0644 \u0639\u0644\u0645\u064a",
        "sci-fi" to "\u062e\u064a\u0627\u0644 \u0639\u0644\u0645\u064a",
        "thriller" to "\u0625\u062b\u0627\u0631\u0629 \u0648\u062a\u0634\u0648\u064a\u0642",
        "movies" to "\u0623\u0641\u0644\u0627\u0645",
        "movie" to "\u0623\u0641\u0644\u0627\u0645",
        "series" to "\u0645\u0633\u0644\u0633\u0644\u0627\u062a",
        "tv shows" to "\u0645\u0633\u0644\u0633\u0644\u0627\u062a",
        "tv" to "\u062a\u0644\u0641\u0632\u064a\u0648\u0646",
        "discover" to "\u0627\u0633\u062a\u0643\u0634\u0641",
        "continue watching" to "\u0645\u062a\u0627\u0628\u0639\u0629 \u0627\u0644\u0645\u0634\u0627\u0647\u062f\u0629",
        "recommended" to "\u0645\u0648\u0635\u0649 \u0628\u0647",
        "most watched" to "\u0627\u0644\u0623\u0643\u062b\u0631 \u0645\u0634\u0627\u0647\u062f\u0629",
        "box office" to "\u0634\u0628\u0627\u0643 \u0627\u0644\u062a\u0630\u0627\u0643\u0631",
        "collections" to "\u0627\u0644\u0633\u0644\u0627\u0633\u0644 \u0648\u0627\u0644\u0645\u062c\u0645\u0648\u0639\u0627\u062a",
        "war" to "\u062d\u0631\u0628",
        "western" to "\u063a\u0631\u0628 \u0623\u0645\u0631\u064a\u0643\u064a",
        "biography" to "\u0633\u064a\u0631\u0629 \u0630\u0627\u062a\u064a\u0629",
        "sport" to "\u0631\u064a\u0627\u0636\u0629",
        "music" to "\u0645\u0648\u0633\u064a\u0642\u0649" 
    )
    var res = raw
    for ((en, ar) in map) {
        if (res.equals(en, ignoreCase = true)) return ar
        val regex = Regex("\\b" + Regex.escape(en) + "\\b", RegexOption.IGNORE_CASE)
        if (regex.containsMatchIn(res)) {
            res = regex.replace(res, ar)
        }
    }
    return res
}

