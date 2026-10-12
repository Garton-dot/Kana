package com.garton.kana.data

import com.garton.kana.model.ItemKind
import com.garton.kana.model.StudyItem
import com.garton.kana.model.StudyUnit
import com.garton.kana.model.Track

/** Lessons 1–5 of beginner vocabulary. Format: kana | romaji | meaning | kanji (optional). */
private val LESSONS: List<Triple<String, String, String>> = listOf(
    Triple("Lesson 1", "Greetings & people", """
        おはよう|ohayou|good morning
        こんにちは|konnichiwa|hello
        こんばんは|konbanwa|good evening
        さようなら|sayounara|goodbye
        おやすみなさい|oyasuminasai|good night
        ありがとう|arigatou|thank you
        すみません|sumimasen|excuse me / sorry
        はい|hai|yes
        いいえ|iie|no
        はじめまして|hajimemashite|nice to meet you
        よろしく|yoroshiku|pleased to meet you
        わたし|watashi|I / me|私
        なまえ|namae|name|名前
        がくせい|gakusei|student|学生
        せんせい|sensei|teacher|先生
        だいがく|daigaku|university|大学
        ともだち|tomodachi|friend|友達
        にほん|nihon|Japan|日本
        にほんご|nihongo|Japanese language|日本語
        アメリカ|amerika|America
        いち|ichi|one|一
        に|ni|two|二
        さん|san|three|三
        よん|yon|four|四
        ご|go|five|五
    """),
    Triple("Lesson 2", "Things & shopping", """
        これ|kore|this one
        それ|sore|that one
        あれ|are|that one over there
        どれ|dore|which one
        ここ|koko|here
        そこ|soko|there
        いくら|ikura|how much
        ほん|hon|book|本
        かばん|kaban|bag
        くつ|kutsu|shoes|靴
        さいふ|saifu|wallet|財布
        かさ|kasa|umbrella|傘
        とけい|tokei|watch / clock|時計
        ぼうし|boushi|hat|帽子
        じしょ|jisho|dictionary|辞書
        えんぴつ|enpitsu|pencil|鉛筆
        ペン|pen|pen
        みず|mizu|water|水
        おちゃ|ocha|green tea|お茶
        コーヒー|koohii|coffee
        にく|niku|meat|肉
        さかな|sakana|fish|魚
        ひゃく|hyaku|hundred|百
        せん|sen|thousand|千
        えん|en|yen|円
    """),
    Triple("Lesson 3", "Daily life", """
        いく|iku|to go|行く
        かえる|kaeru|to go home|帰る
        くる|kuru|to come|来る
        する|suru|to do
        たべる|taberu|to eat|食べる
        のむ|nomu|to drink|飲む
        みる|miru|to see / watch|見る
        きく|kiku|to listen|聞く
        よむ|yomu|to read|読む
        はなす|hanasu|to speak|話す
        おきる|okiru|to get up|起きる
        ねる|neru|to sleep|寝る
        べんきょう|benkyou|study|勉強
        うち|uchi|home / house|家
        がっこう|gakkou|school|学校
        としょかん|toshokan|library|図書館
        えいが|eiga|movie|映画
        テレビ|terebi|TV
        あさごはん|asagohan|breakfast|朝ご飯
        ばんごはん|bangohan|dinner|晩ご飯
        きょう|kyou|today|今日
        あした|ashita|tomorrow|明日
        まいにち|mainichi|every day|毎日
        あさ|asa|morning|朝
        よる|yoru|night|夜
    """),
    Triple("Lesson 4", "Places & time", """
        ある|aru|to exist (things)
        いる|iru|to exist (people/animals)
        みせ|mise|store|店
        えき|eki|station|駅
        びょういん|byouin|hospital|病院
        こうえん|kouen|park|公園
        レストラン|resutoran|restaurant
        トイレ|toire|toilet
        うえ|ue|on / above|上
        した|shita|under|下
        まえ|mae|in front|前
        うしろ|ushiro|behind|後ろ
        なか|naka|inside|中
        となり|tonari|next to|隣
        みぎ|migi|right|右
        ひだり|hidari|left|左
        きのう|kinou|yesterday|昨日
        じかん|jikan|time / hours|時間
        しゃしん|shashin|photograph|写真
        いぬ|inu|dog|犬
        ねこ|neko|cat|猫
        こども|kodomo|child|子供
        にちようび|nichiyoubi|Sunday|日曜日
        どようび|doyoubi|Saturday|土曜日
        げつようび|getsuyoubi|Monday|月曜日
    """),
    Triple("Lesson 5", "Adjectives & travel", """
        あつい|atsui|hot|暑い
        さむい|samui|cold (weather)|寒い
        おおきい|ookii|big|大きい
        ちいさい|chiisai|small|小さい
        あたらしい|atarashii|new|新しい
        ふるい|furui|old (things)|古い
        たかい|takai|expensive / tall|高い
        やすい|yasui|cheap|安い
        おいしい|oishii|delicious
        おもしろい|omoshiroi|interesting / funny
        たのしい|tanoshii|fun|楽しい
        むずかしい|muzukashii|difficult|難しい
        いそがしい|isogashii|busy|忙しい
        いい|ii|good
        こわい|kowai|scary|怖い
        しずか|shizuka|quiet|静か
        きれい|kirei|beautiful / clean
        げんき|genki|healthy / energetic|元気
        すき|suki|to like|好き
        きらい|kirai|to dislike|嫌い
        ひま|hima|free (time)|暇
        うみ|umi|sea|海
        やま|yama|mountain|山
        てんき|tenki|weather|天気
        りょこう|ryokou|travel|旅行
    """),
)

val VOCAB_UNITS: List<StudyUnit> = LESSONS.mapIndexed { index, (title, subtitle, raw) ->
    val unitId = "v-${index + 1}"
    val items = raw.trim().lines().map { line ->
        val parts = line.trim().split("|")
        StudyItem(
            id = "v:${parts[0]}",
            kind = ItemKind.VOCAB,
            japanese = parts[0],
            romaji = parts[1],
            meaning = parts[2],
            kanji = parts.getOrNull(3),
            unitId = unitId,
        )
    }
    StudyUnit(unitId, Track.VOCAB, title, subtitle, items)
}
