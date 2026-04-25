# Quiz Leaderboard System
Polls API 10 times, deduplicates events using roundId+participant key, aggregates scores and submits leaderboard.

## Run
javac -cp json-20240303.jar QuizLeaderboard.java -d out
java -cp "out;json-20240303.jar" com.quiz.QuizLeaderboard

## Result
Bob: 295 | Alice: 280 | Charlie: 260 | Total: 835
