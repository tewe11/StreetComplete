package de.wp.streetcomplete.data.osm.persist.test

import de.westnordost.backbiking.R

class DisabledTestQuestType : de.wp.streetcomplete.data.osm.persist.test.TestQuestType() {
    override val defaultDisabledMessage = R.string.default_disabled_msg_go_inside
}
