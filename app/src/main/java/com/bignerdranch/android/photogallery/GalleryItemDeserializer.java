package com.bignerdranch.android.photogallery;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

public class GalleryItemDeserializer implements JsonDeserializer<GalleryItem>
{
    @Override
    public GalleryItem deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
    {
        JsonObject photoJsonObject = json.getAsJsonObject();

        if (!photoJsonObject.has("url_s") ||
            !photoJsonObject.has("title")) return null;

        GalleryItem item = new GalleryItem();

        item.setId(photoJsonObject.get("id").getAsString());
        item.setCaption(photoJsonObject.get("title").getAsString());
        item.setUrl(photoJsonObject.get("url_s").getAsString());
        item.setOwner(photoJsonObject.get("owner").getAsString());


        return item;
    }
}
