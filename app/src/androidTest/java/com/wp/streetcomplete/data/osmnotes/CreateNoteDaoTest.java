package de.wp.streetcomplete.data.osmnotes;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import de.wp.streetcomplete.data.ApplicationDbTestCase;
import de.westnordost.osmapi.map.data.BoundingBox;
import de.westnordost.osmapi.map.data.Element;
import de.westnordost.osmapi.map.data.OsmLatLon;
import de.westnordost.backbiking.data.osmnotes.CreateNote;
import de.westnordost.backbiking.data.osmnotes.CreateNoteDao;

import static org.junit.Assert.*;

public class CreateNoteDaoTest extends ApplicationDbTestCase
{
	private CreateNoteDao dao;

	@Before public void createDao()
	{
		dao = new CreateNoteDao(dbHelper, serializer);
	}

	@Test public void addGetDelete()
	{
		CreateNote note = new CreateNote();
		note.text = "text";
		note.questTitle = "title";
		note.position = new OsmLatLon(3,5);
		note.elementId = 123L;
		note.elementType = Element.Type.NODE;
		note.imagePaths = new ArrayList<>();
		note.imagePaths.add("hullo");
		note.imagePaths.add("hey");

		assertTrue(dao.add(note));
		CreateNote dbNote = dao.get(note.id);

		assertEquals(note.text, dbNote.text);
		assertEquals(note.questTitle, dbNote.questTitle);
		assertEquals(note.id, dbNote.id);
		assertEquals(note.position, dbNote.position);
		assertEquals(note.elementType, dbNote.elementType);
		assertEquals(note.elementId, dbNote.elementId);
		assertEquals(note.imagePaths, dbNote.imagePaths);

		assertTrue(dao.delete(note.id));

		assertNull(dao.get(note.id));
	}

	@Test public void nullable()
	{
		CreateNote note = new CreateNote();
		note.text = "text";
		note.position = new OsmLatLon(3,5);

		assertTrue(dao.add(note));
		CreateNote dbNote = dao.get(note.id);

		assertNull(dbNote.elementType);
		assertNull(dbNote.elementId);
		assertNull(dbNote.questTitle);
		assertNull(dbNote.imagePaths);
	}

	@Test public void getAll()
	{
		CreateNote note1 = new CreateNote();
		note1.text = "this is in";
		note1.position = new OsmLatLon(0.5,0.5);
		dao.add(note1);

		CreateNote note2 = new CreateNote();
		note2.text = "this is out";
		note2.position = new OsmLatLon(-0.5,0.5);
		dao.add(note2);

		assertEquals(1,dao.getAll(new BoundingBox(0,0,1,1)).size());

		assertEquals(2,dao.getAll(null).size());
	}


	@Test public void countNone()
	{
		assertEquals(0, dao.getCount());
	}

	@Test public void countOne()
	{
		CreateNote note1 = new CreateNote();
		note1.position = new OsmLatLon(0.5,0.5);
		note1.text = "joho";
		dao.add(note1);

		assertEquals(1, dao.getCount());
	}

	@Test public void countSeveral()
	{
		CreateNote note1 = new CreateNote();
		note1.position = new OsmLatLon(0.5,0.5);
		note1.text = "joho";
		dao.add(note1);

		CreateNote note2 = new CreateNote();
		note2.position = new OsmLatLon(0.1,0.5);
		note2.text = "hey";
		dao.add(note2);

		assertEquals(2, dao.getCount());
	}
}
