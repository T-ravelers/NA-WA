package me.nawa.ingest.service;

import me.nawa.ingest.dto.request.EventIngestItem;
import me.nawa.ingest.dto.request.EventTranslationIngestItem;
import me.nawa.ingest.dto.request.PlaceIngestItem;
import me.nawa.ingest.dto.request.PlaceTranslationIngestItem;
import me.nawa.ingest.dto.response.IngestResultResponse;

import java.util.List;

public interface IngestService {

    IngestResultResponse ingestEvents(List<EventIngestItem> items);

    IngestResultResponse ingestPlaces(List<PlaceIngestItem> items);

    IngestResultResponse ingestEventTranslations(List<EventTranslationIngestItem> items);

    IngestResultResponse ingestPlaceTranslations(List<PlaceTranslationIngestItem> items);
}
