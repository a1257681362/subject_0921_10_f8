package com.evops.mixalgo.engine;

import com.evops.mixalgo.dto.MixParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 同校错开混排引擎（纯函数式、可复现）：
 * 给定名册、考场容量、算法参数与随机种子，输出座次与同校相邻统计。
 *
 * 两步：
 * 1) 分房——同校学员按各考场占用名额（与容量成正比）尽量摊到多个考场：
 *    学员按"剩余人数最多的学校"轮取，每名人进"剩余名额最多"的考场，
 *    使任一学校在各考场的人数差不超过 1；
 * 2) 排座——每个考场内按学校交错入座（最大剩余学校优先，避开最近 minSchoolGap 个座位的同校），
 *    并用 seeds 衍生多次随机试排，取同校相邻最少的一次。
 *
 * 未填学校的学员视为各自独立的"未知"，互不计同校相邻。
 */
public final class SeatingMixer {

    private SeatingMixer() {
    }

    /** 座次表学员输入。 */
    public static class Student {
        public final String key;
        public final String name;
        /** 为空表示未知学校 */
        public final String school;

        public Student(String key, String name, String school) {
            this.key = key;
            this.name = name;
            this.school = school;
        }
    }

    /** 考场输入。 */
    public static class Room {
        public final Long roomId;
        public final String roomNo;
        public final int capacity;

        public Room(Long roomId, String roomNo, int capacity) {
            this.roomId = roomId;
            this.roomNo = roomNo;
            this.capacity = capacity;
        }
    }

    /** 单条座位分配。 */
    public static class Assignment {
        public final Student student;
        public final Long roomId;
        public final String roomNo;
        public final int seatNo;

        public Assignment(Student student, Long roomId, String roomNo, int seatNo) {
            this.student = student;
            this.roomId = roomId;
            this.roomNo = roomNo;
            this.seatNo = seatNo;
        }
    }

    /** 一对同校相邻座位（巡考抽查明细）。 */
    public static class AdjacentPair {
        public String roomNo;
        public int seatA;
        public int seatB;
        public String school;
        public String keyA;
        public String nameA;
        public String keyB;
        public String nameB;
    }

    /** 混排结果：按考场顺序、座位号升序的分配清单与统计。 */
    public static class Plan {
        public long seed;
        public List<Assignment> assignments = new ArrayList<>();
        public List<AdjacentPair> adjacentPairs = new ArrayList<>();
        /** 考场 -> 学校 -> 人数 */
        public Map<String, Map<String, Integer>> schoolCountByRoom = new LinkedHashMap<>();
        public int assignedCount;
        public int usedRoomCount;
    }

    /**
     * 执行混排。调用方须先保证总容量 ≥ 学员数；容量不足时超出的学员不进结果。
     */
    public static Plan mix(List<Student> students, List<Room> rooms, MixParams params, long seed) {
        Plan best = null;
        int tries = params == null || params.getTries() == null ? 1
                : Math.max(1, params.getTries());
        for (int t = 0; t < tries; t++) {
            long trySeed = seed + 0x9E3779B97F4A7C15L * (t + 1);
            Plan candidate = mixOnce(students, rooms, params, trySeed);
            if (best == null || candidate.adjacentPairs.size() < best.adjacentPairs.size()) {
                best = candidate;
            }
            if (best.adjacentPairs.isEmpty()) {
                break;
            }
        }
        best.seed = seed;
        return best;
    }

    private static Plan mixOnce(List<Student> students, List<Room> rooms, MixParams params, long seed) {
        Random random = new Random(seed);
        int gap = params == null || params.getMinSchoolGap() == null ? 1
                : Math.max(1, params.getMinSchoolGap());
        boolean spreadRooms = params == null || params.getSpreadRooms() == null
                || params.getSpreadRooms();
        Integer maxPerRoom = params == null ? null : params.getMaxSameSchoolPerRoom();

        // 各考场实际占用名额：按考场顺序从 1 号座填满，总名额 = 学员数（容量已由调用方校验）
        int[] quota = new int[rooms.size()];
        int remaining = students.size();
        int totalCap = 0;
        for (Room room : rooms) {
            totalCap += room.capacity;
        }
        remaining = Math.min(remaining, totalCap);
        for (int j = 0; j < rooms.size() && remaining > 0; j++) {
            quota[j] = Math.min(rooms.get(j).capacity, remaining);
            remaining -= quota[j];
        }

        // 按学校分组（未知学校各成一组），组内先用本次随机种子洗牌
        Map<String, List<Student>> groups = new LinkedHashMap<>();
        int unknownIdx = 0;
        for (Student s : students) {
            String groupKey = (s.school == null || s.school.trim().isEmpty())
                    ? "\0UNKNOWN_" + (unknownIdx++) : s.school.trim();
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(s);
        }
        for (List<Student> g : groups.values()) {
            Collections.shuffle(g, random);
        }

        // 分房：剩余人数最多的学校轮取，进剩余名额最多的考场（尽量摊开、遵守单房上限）
        int[] roomTaken = new int[rooms.size()];
        Map<String, int[]> schoolRoomCount = new LinkedHashMap<>();
        List<Student>[] inRoom = new List[rooms.size()];
        for (int j = 0; j < rooms.size(); j++) {
            inRoom[j] = new ArrayList<>();
        }
        List<String> groupKeys = new ArrayList<>(groups.keySet());
        while (true) {
            String pick = null;
            int maxRemain = 0;
            List<String> tied = new ArrayList<>();
            for (String gk : groupKeys) {
                int remain = groups.get(gk).size();
                if (remain <= 0) {
                    continue;
                }
                if (remain > maxRemain) {
                    maxRemain = remain;
                    tied.clear();
                    tied.add(gk);
                } else if (remain == maxRemain) {
                    tied.add(gk);
                }
            }
            if (maxRemain == 0) {
                break;
            }
            pick = tied.get(random.nextInt(tied.size()));

            int target = chooseRoom(rooms, quota, roomTaken,
                    schoolRoomCount.computeIfAbsent(pick, k -> new int[rooms.size()]),
                    spreadRooms, maxPerRoom, random);
            if (target < 0) {
                // 理论上容量校验后不会发生；保底找任意有名额的房
                for (int j = 0; j < rooms.size(); j++) {
                    if (roomTaken[j] < quota[j]) {
                        target = j;
                        break;
                    }
                }
            }
            if (target < 0) {
                break;
            }
            Student s = groups.get(pick).remove(groups.get(pick).size() - 1);
            inRoom[target].add(s);
            roomTaken[target]++;
            schoolRoomCount.get(pick)[target]++;
        }

        // 排座：房内交错入座 + 同校相邻统计
        Plan plan = new Plan();
        plan.seed = seed;
        for (int j = 0; j < rooms.size(); j++) {
            List<Student> seated = interleaveInRoom(inRoom[j], gap, random);
            if (seated.isEmpty()) {
                continue;
            }
            plan.usedRoomCount++;
            Map<String, Integer> schoolCounts = new LinkedHashMap<>();
            for (int i = 0; i < seated.size(); i++) {
                Student s = seated.get(i);
                Assignment a = new Assignment(s, rooms.get(j).roomId, rooms.get(j).roomNo, i + 1);
                plan.assignments.add(a);
                String sk = s.school == null || s.school.trim().isEmpty() ? "未填学校" : s.school.trim();
                schoolCounts.merge(sk, 1, Integer::sum);
                if (i > 0) {
                    Student prev = seated.get(i - 1);
                    if (s.school != null && !s.school.trim().isEmpty()
                            && s.school.trim().equals(prev.school == null ? null : prev.school.trim())) {
                        AdjacentPair pair = new AdjacentPair();
                        pair.roomNo = rooms.get(j).roomNo;
                        pair.seatA = i;
                        pair.seatB = i + 1;
                        pair.school = s.school.trim();
                        pair.keyA = prev.key;
                        pair.nameA = prev.name;
                        pair.keyB = s.key;
                        pair.nameB = s.name;
                        plan.adjacentPairs.add(pair);
                    }
                }
            }
            plan.schoolCountByRoom.put(rooms.get(j).roomNo, schoolCounts);
        }
        plan.assignedCount = plan.assignments.size();
        return plan;
    }

    /**
     * 房内交错：每次从"剩余最多且学校不在最近 gap 个已放座位"的学校取人；
     * 无法满足间隔时退而取剩余最多的学校（尽力而为，不可行不强造空座）。
     */
    private static List<Student> interleaveInRoom(List<Student> roomStudents, int gap, Random random) {
        Map<String, List<Student>> buckets = new LinkedHashMap<>();
        int unknownSeq = 0;
        for (Student s : roomStudents) {
            String key = (s.school == null || s.school.trim().isEmpty())
                    ? "\0U_" + (unknownSeq++) : s.school.trim();
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }
        List<String> recent = new ArrayList<>();
        List<Student> seated = new ArrayList<>();
        int total = roomStudents.size();
        while (seated.size() < total) {
            String chosen = chooseBucket(buckets, recent, gap, random, false);
            if (chosen == null) {
                chosen = chooseBucket(buckets, recent, gap, random, true);
            }
            if (chosen == null) {
                break;
            }
            List<Student> bucket = buckets.get(chosen);
            seated.add(bucket.remove(bucket.size() - 1));
            if (bucket.isEmpty()) {
                buckets.remove(chosen);
            }
            recent.add(chosen);
            if (recent.size() > gap) {
                recent.remove(0);
            }
        }
        return seated;
    }

    private static String chooseBucket(Map<String, List<Student>> buckets, List<String> recent,
                                       int gap, Random random, boolean ignoreGap) {
        List<String> best = new ArrayList<>();
        int maxRemain = 0;
        for (Map.Entry<String, List<Student>> e : buckets.entrySet()) {
            if (!ignoreGap && recent.size() >= gap && recent.subList(recent.size() - gap, recent.size())
                    .contains(e.getKey())) {
                continue;
            }
            int n = e.getValue().size();
            if (n > maxRemain) {
                maxRemain = n;
                best.clear();
                best.add(e.getKey());
            } else if (n == maxRemain) {
                best.add(e.getKey());
            }
        }
        return best.isEmpty() ? null : best.get(random.nextInt(best.size()));
    }

    private static int chooseRoom(List<Room> rooms, int[] quota, int[] roomTaken, int[] schoolCount,
                                  boolean spreadRooms, Integer maxPerRoom, Random random) {
        int best = -1;
        int bestRemain = -1;
        List<Integer> tied = new ArrayList<>();
        for (int j = 0; j < rooms.size(); j++) {
            if (roomTaken[j] >= quota[j]) {
                continue;
            }
            if (spreadRooms && maxPerRoom != null && schoolCount[j] >= maxPerRoom) {
                continue;
            }
            int remain = quota[j] - roomTaken[j];
            if (remain > bestRemain) {
                bestRemain = remain;
                tied.clear();
                tied.add(j);
            } else if (remain == bestRemain) {
                tied.add(j);
            }
        }
        if (!tied.isEmpty()) {
            return tied.get(random.nextInt(tied.size()));
        }
        // 单房上限导致无处可去时，放开上限保证排得下（上限为尽力约束）
        if (spreadRooms && maxPerRoom != null) {
            for (int j = 0; j < rooms.size(); j++) {
                if (roomTaken[j] < quota[j]) {
                    return j;
                }
            }
        }
        return best;
    }

    /** 统计口径：相邻定义为同考场座位号连续，可对已落库座次复用。 */
    public static List<AdjacentPair> adjacencyOf(List<Assignment> assignments) {
        List<Assignment> ordered = new ArrayList<>(assignments);
        ordered.sort(Comparator.comparing((Assignment a) -> a.roomNo).thenComparingInt(a -> a.seatNo));
        List<AdjacentPair> pairs = new ArrayList<>();
        Assignment prev = null;
        for (Assignment a : ordered) {
            if (prev != null && prev.roomNo.equals(a.roomNo) && a.seatNo == prev.seatNo + 1
                    && a.student.school != null && !a.student.school.trim().isEmpty()
                    && a.student.school.trim().equals(
                            prev.student.school == null ? null : prev.student.school.trim())) {
                AdjacentPair p = new AdjacentPair();
                p.roomNo = a.roomNo;
                p.seatA = prev.seatNo;
                p.seatB = a.seatNo;
                p.school = a.student.school.trim();
                p.keyA = prev.student.key;
                p.nameA = prev.student.name;
                p.keyB = a.student.key;
                p.nameB = a.student.name;
                pairs.add(p);
            }
            prev = a;
        }
        return pairs;
    }
}
