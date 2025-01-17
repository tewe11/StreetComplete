package de.wp.streetcomplete.data.osm.persist.test

import de.westnordost.osmapi.map.data.BoundingBox
import de.westnordost.osmapi.map.data.Element
import de.westnordost.backbiking.data.osm.OsmElementQuestType
import de.westnordost.backbiking.data.osm.changes.StringMapChangesBuilder
import de.westnordost.backbiking.data.osm.download.MapDataWithGeometryHandler
import de.westnordost.backbiking.quests.AbstractQuestAnswerFragment

open class TestQuestType : OsmElementQuestType<String> {

    override fun getTitle(tags: Map<String, String>) = 0
    override fun download(bbox: BoundingBox, handler: MapDataWithGeometryHandler) = false
    override fun isApplicableTo(element: Element):Boolean? = null
    override fun applyAnswerTo(answer: String, changes: StringMapChangesBuilder) {}
    override val icon = 0
    override fun createForm(): AbstractQuestAnswerFragment<String> = object : AbstractQuestAnswerFragment<String>() {}
    override val commitMessage = ""
}
