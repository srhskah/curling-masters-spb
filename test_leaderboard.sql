-- 检查已完成比赛数量（status=2）
SELECT COUNT(*) as completed_matches FROM `match` WHERE status = 2;

-- 检查有选手ID的比赛数量
SELECT COUNT(*) as matches_with_players FROM `match` WHERE status = 2 AND player1_id IS NOT NULL;

-- 检查每个选手的比赛数量（至少5场）
SELECT player1_id, COUNT(*) as match_count 
FROM `match` 
WHERE status = 2 AND player1_id IS NOT NULL 
GROUP BY player1_id 
HAVING match_count >= 5;

-- 检查是否有set_score数据
SELECT COUNT(*) as set_score_count FROM set_score WHERE player1_score IS NOT NULL;

-- 检查是否有hammer_player_id数据
SELECT COUNT(*) as hammer_count FROM set_score WHERE hammer_player_id IS NOT NULL AND hammer_player_id <> 0;