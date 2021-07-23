import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.ceil

/* 入力値：
*　入店日時（DateTimeImmutableクラス）※PHP以外の言語の場合は左記クラスに相当するクラスを使用すること
*　退店日時（DateTimeImmutableクラス）※PHP以外の言語の場合は左記クラスに相当するクラスを使用すること
*　コースの種別
*
*  要件：
*  ・コースは入店時のみ決定することができ、コースの時間を過ぎた場合は自動的に10分ごとに延長料金が発生する。
*  ・1分でも過ぎた場合延長の対象となる（1秒でも超過していれば切り上げで延長とする）。
*  ・延長料金には深夜割増があり、22:00〜翌朝5:00までは深夜料金として15%割増した金額を合計する。
*  ・1分でも深夜時間に含まれていれば深夜料金として金額を割増、計上する。
*  ・税込、税抜両方の金額を算出できる機能とすること。
*/
class MangaCafeBill(val courseName: String, val timeEntered: ZonedDateTime, val timeExited: ZonedDateTime) {
    // コースの税抜き価格初期値(円)
    var courseFee: Int = 0

    // コース時間
    var courseHours: Int = 0

    // 任意の「分」毎をベースに延長発生。
    val extensionBase: Int = 10

    // 延長発生毎にチャージされる、税抜延長料金(円)
    val extensionBaseFee: Int = 100

    // 深夜料金の割り増し倍率
    val nightFeePercentage: Double = 1.15

    // 延長発生毎にチャージされる、税抜「深夜」延長料金(円)。
    val extensionNightFee: Int = (ceil(extensionBaseFee * nightFeePercentage)).toInt()

    // 入力されたコースの種別によりコース料金・コース時間の長さを設定。該当するコース名がない場合、メッセージを出力。
    init {
        when (courseName) {
            "通常料金" -> {
                courseFee = 500
                courseHours = 1
            }
            "3時間パック" -> {
                courseFee = 800
                courseHours = 3
            }
            "5時間パック" -> {
                courseFee = 1500
                courseHours = 5
            }
            "8時間パック" -> {
                courseFee = 1900
                courseHours = 8
            }
            else -> {throw Exception("入力されたコース名が存在しません。")}
        }
    }

    // 入店・退店時刻を元にご利用時間を算出。
    fun timeAtCafe(): Duration {
        return Duration.between(timeEntered, timeExited)
    }

    // 延長料金計算 (22:00〜翌朝5:00は深夜料金として15%割増した金額を合計する。1分でも深夜時間に含まれていれば金額を割増、計上する。)
    fun extensionFee(): Int {
        // 合計延長金額
        var totalExtensionFee: Int = 0

        // LocalTimeのフォーマットを決定。
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.JAPANESE)

        // 深夜時間帯の開始・終了時刻をLocalTimeで設定。
        val nightStartsAt: LocalTime = LocalTime.parse("22:00:00", formatter)
        val nightEndsAt: LocalTime = LocalTime.parse("05:00:00", formatter)

        // ループ内で「分」毎に深夜時間帯内であるかチェックする対象の時刻。初期値は延長開始時刻。
        var checkIfNight: ZonedDateTime = timeEntered.plusHours(courseHours.toLong())

        var isNight: Boolean = false

        /*延長料金計算 「checkIfNight」を「nightStartsAt」&「nightEndsAt」と比較する為に、その都度checkIfNightをLocalTimeに変更。
        * チェック対象の時刻「checkIfNight」が退店時間前である限り発動するwhileループ。
        * 延長が発生する分数(例：10分)毎に、深夜時間帯に該当する時刻があるかを一分単位でチェックするforループ。
        * 深夜時間帯であれば、深夜料金を合計に追加。それ以外の場合は通常の延長料金を追加します。
        */
        var count:Int = 0
        while (checkIfNight.isBefore(timeExited)) {
            while (count <= extensionBase) {
                if (checkIfNight.toLocalTime().isAfter(nightStartsAt) ||
                    checkIfNight.toLocalTime().isBefore(nightEndsAt)
                ) {
                    isNight = true
                }
                checkIfNight = checkIfNight.plusMinutes(1)
                count += 1
                if (checkIfNight.isAfter(timeExited)) {
                    break
                }
            }
            count = 0
            if (isNight) {
                totalExtensionFee += extensionNightFee
                isNight = false
            } else {
                totalExtensionFee += extensionBaseFee
            }
        }
        return totalExtensionFee
    }

    // ご利用時間(分）を計算。1秒でも延長時間があれば、extensionBase分の延長としてカウントする。
    fun extensionInMinutes(): Long {
        val extension: Duration = timeAtCafe().minusHours(courseHours.toLong())
        // 延長が一秒でもあれば実行。
        if ((extension.toSeconds()) > 0) {
            // 延長時間の「分」の位をextensionBase単位に切り上げる。例：extensionBase = 10の場合、「1時間21分」を「1時間30分」にする。
            if ((extension.toMinutesPart() % extensionBase) > 0) {
                return extension.truncatedTo(ChronoUnit.HOURS)
                    .plusMinutes(
                        extensionBase * ceil(extension.toMinutesPart() / extensionBase.toDouble())
                            .toLong()
                    )
                    .toMinutes()
            }
            // 「秒」の値に1秒でも延長時間があれば、extensionBase分の延長としてカウントする。
            else if (extension.toSecondsPart() > 0) {
                return extension.plusMinutes(extensionBase.toLong()).toMinutes()
            } else {
                return extension.toMinutes()
            }
        } else {
            // コース終了日時より早く退店・コース終了と同時に退店した場合は延長時間なし。
            return 0
        }
    }

    // 合計料金計算(税抜)
    fun totalFee(): Int {
        return extensionFee() + courseFee
    }

    // 税込価格計算(消費税１０％)
    fun taxed(value: Int): Int {
        return (value * 1.1).toInt()
    }

    // 伝票出力
    fun printBill() {
        val printFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", Locale.JAPANESE)

        println("_______________________________")
        println("ご利用コース：${courseName}")
        println("ご利用開始：${timeEntered.format(printFormatter)}")
        println("ご利用終了：${timeExited.format(printFormatter)}")
        println(
            "ご利用時間：${
                String.format(
                    "%d時間 %d分 %d秒",
                    timeAtCafe().toHoursPart(), timeAtCafe().toMinutesPart(), timeAtCafe().toSecondsPart()
                )
            }"
        )
        println("延長(${extensionBase}分毎)：${extensionInMinutes()}分 (${extensionInMinutes() / extensionBase}回)")
        println("コース料金(税抜)：${courseFee}円")
        println("延長料金(税抜)：${extensionFee()}円")
        println("合計(税抜)：${totalFee()}円 (税込 ${taxed(totalFee())}円)")
    }
}

fun main(args: Array<String>) {

    // 料金計算1
    val bill1 = MangaCafeBill(
        "通常料金",
        ZonedDateTime.parse("2021-07-17T10:10:30+09:00[Asia/Tokyo]"),
        ZonedDateTime.parse("2021-07-17T11:10:31+09:00[Asia/Tokyo]")
    )
    bill1.printBill()


    // 料金計算2
    val bill2 = MangaCafeBill(
        "5時間パック",
        ZonedDateTime.parse("2021-07-17T10:00:28+09:00[Asia/Tokyo]"),
        ZonedDateTime.parse("2021-07-17T17:21:28+09:00[Asia/Tokyo]")
    )
    bill2.printBill()


    // 料金計算3
    val bill3 = MangaCafeBill(
        "5時間パック",
        ZonedDateTime.parse("2021-07-17T23:00:00+09:00[Asia/Tokyo]"),
        ZonedDateTime.parse("2021-07-18T07:20:01+09:00[Asia/Tokyo]")
    )
    bill3.printBill()


    // 料金計算4
    val bill4 = MangaCafeBill(
        "3時間パック",
        ZonedDateTime.parse("2021-07-17T23:00:00+09:00[Asia/Tokyo]"),
        ZonedDateTime.parse("2021-07-18T03:20:00+09:00[Asia/Tokyo]")
    )
    bill4.printBill()
}
