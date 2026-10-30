package com.perfumerlab.scentstudio.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.perfumerlab.scentstudio.engine.CatalogModel.Accord;
import com.perfumerlab.scentstudio.engine.CatalogModel.Catalog;
import com.perfumerlab.scentstudio.engine.CatalogModel.Family;
import com.perfumerlab.scentstudio.engine.CatalogModel.Note;
import com.perfumerlab.scentstudio.engine.CatalogModel.Perfume;
import com.perfumerlab.scentstudio.engine.CatalogModel.Tier;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** 테스트용: 가짜 카탈로그와 골든 파일 읽기 (앱 폴더 기준 상대 경로) */
final class TestCatalogs {
    private TestCatalogs() {}

    static final Path APP_DIR = Path.of("..").toAbsolutePath().normalize();

    static Catalog fixtureCatalog() {
        Path p = APP_DIR.resolve("app/src/test/resources/catalog/test_catalog.json.gz");
        try (Reader r = new InputStreamReader(new GZIPInputStream(Files.newInputStream(p)), StandardCharsets.UTF_8)) {
            return parse(JsonParser.parseReader(r).getAsJsonObject());
        } catch (IOException e) {
            throw new IllegalStateException("가짜 카탈로그가 없습니다: py pipeline/build_catalog.py --fixture", e);
        }
    }

    static JsonObject golden() {
        Path p = APP_DIR.resolve("tests/golden/recommend_cases.json");
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("골든 파일이 없습니다: py pipeline/make_golden.py", e);
        }
    }

    static Catalog parse(JsonObject o) {
        List<Family> families = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("families")) {
            JsonObject f = e.getAsJsonObject();
            families.add(new Family(f.get("code").getAsString(), f.get("nameKo").getAsString(), f.get("wheelOrder").getAsInt()));
        }
        List<Accord> accords = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("accords")) {
            JsonObject a = e.getAsJsonObject();
            accords.add(new Accord(a.get("key").getAsString(), a.get("nameKo").getAsString(), a.get("family").getAsString()));
        }
        List<Note> notes = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("notes")) {
            JsonObject n = e.getAsJsonObject();
            JsonArray prof = n.getAsJsonArray("profile");
            double[] profile = new double[prof.size()];
            for (int i = 0; i < profile.length; i++) profile[i] = prof.get(i).getAsDouble();
            notes.add(new Note(n.get("key").getAsString(), n.get("nameEn").getAsString(), n.get("nameKo").getAsString(),
                    n.get("family").getAsString(), n.get("df").getAsInt(), Tier.of(n.get("tier").getAsString()),
                    profile, strings(n.getAsJsonArray("pairings")), n.get("description").getAsString()));
        }
        List<Perfume> perfumes = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("perfumes")) {
            JsonObject p = e.getAsJsonObject();
            perfumes.add(new Perfume(p.get("key").getAsString(), p.get("brand").getAsString(), p.get("name").getAsString(),
                    p.get("year").isJsonNull() ? null : p.get("year").getAsInt(),
                    p.get("concentration").isJsonNull() ? null : p.get("concentration").getAsString(),
                    p.get("rating").isJsonNull() ? null : p.get("rating").getAsDouble(),
                    p.get("ratingCount").getAsInt(), strings(p.getAsJsonArray("accords")),
                    strings(p.getAsJsonArray("top")), strings(p.getAsJsonArray("middle")), strings(p.getAsJsonArray("base")),
                    p.get("group").getAsString()));
        }
        JsonObject source = o.getAsJsonObject("source");
        return new Catalog(o.get("version").getAsString(), o.get("engineVersion").getAsInt(),
                source.get("name").getAsString(), source.get("note").getAsString(), families, accords, notes, perfumes);
    }

    static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : a) out.add(e.getAsString());
        return out;
    }
}
