-- 测试胜率排行榜SQL（无season限制）
SELECT m.player1_id as user_id, COUNT(*) as total_matches, SUM(CASE WHEN m.winner_id = m.player1_id THEN 1 ELSE 0 END) as wins 
FROM `match` m 
WHERE m.status = 2 AND m.player1_id IS NOT NULL 
GROUP BY m.player1_id 
HAVING total_matches >= 5 
ORDER BY (wins * 1.0 / total_matches) DESC 
LIMIT 10;