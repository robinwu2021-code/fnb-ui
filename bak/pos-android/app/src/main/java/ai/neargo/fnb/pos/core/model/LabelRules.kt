package ai.neargo.fnb.pos.core.model

/**
 * 价签规则（原型 retail/PriceLabel.tsx + domain/catalog）：价签上印什么，以及标签机指令（TSPL，203 dpi = 8 点 / 毫米）。
 * 预览和指令用同一份文字，保证屏幕上看到的就是打出来的。
 * 发送时中文字体 TSS24.BF2 要求 GB18030 编码——这一步在标签机接入时做（方案待定 3：标签机型号）。
 */
object LabelRules {
    data class Face(val name: String, val price: String, val unit: String, val member: String?, val ean13: String)

    /** 价签内容：名称、售价（称重 /kg）、单位价（称重每 500g）、会员价（低于售价才印）、条码（转成 EAN-13） */
    fun face(p: Product): Face {
        val weigh = p.retail?.weighable == true
        val per500 = BarcodeRules.labelPer500g(p.price, weigh)
        return Face(
            p.name,
            yuan(p.price) + if (weigh) "/kg" else "",
            per500?.let { "每 500g ${yuan(it)}" } ?: "单位：${p.retail?.unit ?: "件"}",
            p.retail?.memberPrice?.takeIf { it < p.price }?.let { "会员价 ${yuan(it)}" },
            BarcodeRules.toEan13(p.retail?.barcodes?.firstOrNull() ?: p.id),
        )
    }

    private fun q(s: String) = s.replace("\"", "'")
    private const val FONT = "TSS24.BF2"

    /** 一张价签的 TSPL 指令；EAN-13 给前 12 位，校验位由标签机算 */
    fun tspl(p: Product, tpl: LabelTpl, copies: Int = 1): List<String> {
        val f = face(p)
        val head = listOf("SIZE ${tpl.wMm} mm,${tpl.hMm} mm", "GAP 2 mm,0 mm", "DIRECTION 1", "CLS")
        val body = when (tpl) {
            LabelTpl.L60x40 -> listOfNotNull(
                "TEXT 16,16,\"$FONT\",0,1,1,\"${q(f.name)}\"",
                "TEXT 16,52,\"$FONT\",0,1,1,\"${q(f.unit)}\"",
                f.member?.let { "TEXT 16,88,\"$FONT\",0,1,1,\"${q(it)}\"" },
                "BARCODE 16,200,\"EAN13\",60,1,0,2,4,\"${f.ean13.take(12)}\"",
                "TEXT 250,220,\"$FONT\",0,2,2,\"${q(f.price)}\"",
            )
            LabelTpl.L40x30 -> listOf(
                "TEXT 12,12,\"$FONT\",0,1,1,\"${q(f.name)}\"",
                "BARCODE 12,140,\"EAN13\",50,1,0,2,4,\"${f.ean13.take(12)}\"",
                "TEXT 12,60,\"$FONT\",0,2,2,\"${q(f.price)}\"",
            )
            LabelTpl.Shelf -> listOfNotNull(
                "TEXT 16,16,\"$FONT\",0,1,1,\"${q(f.name)}\"",
                "TEXT 16,52,\"$FONT\",0,1,1,\"${q(f.unit)}\"",
                "BARCODE 16,96,\"EAN13\",40,1,0,2,4,\"${f.ean13.take(12)}\"",
                "TEXT 420,40,\"$FONT\",0,3,3,\"${q(f.price)}\"",
                f.member?.let { "TEXT 420,130,\"$FONT\",0,1,1,\"${q(it)}\"" },
            )
        }
        return head + body + "PRINT 1,${copies.coerceIn(1, 99)}"
    }
}
