package com.quiz;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Quiz Leaderboard System
 * 
 * Flow:
 *  1. Poll /quiz/messages 10 times (poll=0..9) with 5s delay between each
 *  2. Deduplicate events using (roundId + participant) as composite key
 *  3. Aggregate scores per participant
 *  4. Sort leaderboard by totalScore descending
 *  5. Submit leaderboard once to /quiz/submit
 */
public class QuizLeaderboard {

    // ── CONFIG ──────────────────────────────────────────────────────────────
    private static final String BASE_URL  = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    private static final String REG_NO    = "RA2311026010444";   
    private static final int    TOTAL_POLLS   = 10;
    private static final int    POLL_DELAY_MS = 5000;         // 5-second mandatory delay
    // ────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {

        HttpClient client = HttpClient.newHttpClient();

        // Key = "roundId::participant"  →  used for deduplication
        Set<String>           seen           = new HashSet<>();

        // participant → total score
        Map<String, Integer>  scoreMap       = new LinkedHashMap<>();

        // ── STEP 1 & 2: Poll 10 times ────────────────────────────────────
        System.out.println("=== Starting 10 polls ===\n");

        for (int poll = 0; poll < TOTAL_POLLS; poll++) {

            String url = BASE_URL + "/quiz/messages?regNo=" + REG_NO + "&poll=" + poll;
            System.out.println("Poll " + poll + " → GET " + url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("  Status : " + response.statusCode());
            System.out.println("  Body   : " + response.body());

            // ── STEP 3: Deduplicate & Collect ────────────────────────────
            if (response.statusCode() == 200) {
                JSONObject json   = new JSONObject(response.body());
                JSONArray  events = json.optJSONArray("events");

                if (events != null) {
                    for (int i = 0; i < events.length(); i++) {
                        JSONObject event       = events.getJSONObject(i);
                        String     roundId     = event.getString("roundId");
                        String     participant = event.getString("participant");
                        int        score       = event.getInt("score");

                        // Composite deduplication key
                        String key = roundId + "::" + participant;

                        if (seen.contains(key)) {
                            System.out.println("  [DUPLICATE] Skipping " + key);
                        } else {
                            seen.add(key);
                            scoreMap.merge(participant, score, Integer::sum);
                            System.out.println("  [NEW]       " + key + " → +" + score);
                        }
                    }
                }
            } else {
                System.out.println("  [WARN] Non-200 response on poll " + poll);
            }

            // ── Mandatory 5-second delay (skip after last poll) ──────────
            if (poll < TOTAL_POLLS - 1) {
                System.out.println("  Waiting 5 seconds...\n");
                Thread.sleep(POLL_DELAY_MS);
            }
        }

        // ── STEP 4: Sort leaderboard by totalScore descending ────────────
        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(scoreMap.entrySet());
        sortedEntries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        // ── STEP 5: Build leaderboard JSON & compute total ───────────────
        JSONArray leaderboard = new JSONArray();
        int totalScore = 0;

        System.out.println("\n=== Final Leaderboard ===");
        for (Map.Entry<String, Integer> entry : sortedEntries) {
            JSONObject item = new JSONObject();
            item.put("participant", entry.getKey());
            item.put("totalScore",  entry.getValue());
            leaderboard.put(item);

            totalScore += entry.getValue();
            System.out.printf("  %-20s %d%n", entry.getKey(), entry.getValue());
        }
        System.out.println("  ─────────────────────────");
        System.out.println("  Combined Total Score : " + totalScore);

        // ── STEP 6: Submit once ──────────────────────────────────────────
        JSONObject submitBody = new JSONObject();
        submitBody.put("regNo",       REG_NO);
        submitBody.put("leaderboard", leaderboard);

        System.out.println("\n=== Submitting Leaderboard ===");
        System.out.println("  Payload: " + submitBody.toString(2));

        HttpRequest submitRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/quiz/submit"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(submitBody.toString()))
                .build();

        HttpResponse<String> submitResponse =
                client.send(submitRequest, HttpResponse.BodyHandlers.ofString());

        System.out.println("\n=== Submission Result ===");
        System.out.println("  Status : " + submitResponse.statusCode());
        System.out.println("  Body   : " + submitResponse.body());

        // ── Parse and display result ─────────────────────────────────────
        JSONObject result = new JSONObject(submitResponse.body());
        System.out.println("\n  isCorrect      : " + result.optBoolean("isCorrect"));
        System.out.println("  isIdempotent   : " + result.optBoolean("isIdempotent"));
        System.out.println("  submittedTotal : " + result.optInt("submittedTotal"));
        System.out.println("  expectedTotal  : " + result.optInt("expectedTotal"));
        System.out.println("  message        : " + result.optString("message"));
    }
}
