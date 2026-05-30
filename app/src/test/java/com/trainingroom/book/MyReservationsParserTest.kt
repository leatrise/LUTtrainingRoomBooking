package com.trainingroom.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MyReservationsParserTest {

    @Test
    fun `parse reservation history page extracts rows and paging`() {
        val html = """
            <html>
            <body>
            <div id="tab2">
              <form action="/moretraingroombesklog#tab2" id="frompost" name="frompost">
                <input type="text" id="begintime" name="begintime" value="2025-11-06" />
                <input type="text" id="endtime" name="endtime" value="2026-04-1" />
                <table class="table_type_7 responsive_table full_width t_align_l">
                  <tbody>
                    <tr>
                      <td data-title="状态">已审核</td>
                      <td data-title="研讨间">研讨室A710</td>
                      <td data-title="创建时间">2026-03-28 10:30</td>
                      <td data-title="使用日期">2026-03-28</td>
                      <td data-title="开始时间">14:50:08</td>
                      <td data-title="结束日期">2026-03-28</td>
                      <td data-title="结束时间">17:29:16</td>
                    </tr>
                    <tr>
                      <td data-title="状态"><span>已审核</span></td>
                      <td data-title="研讨间">研讨室A707</td>
                      <td data-title="创建时间">2026-03-24 16:31</td>
                      <td data-title="使用日期">2026-03-24</td>
                      <td data-title="开始时间">19:30:40</td>
                      <td data-title="结束日期">2026-03-24</td>
                      <td data-title="结束时间">21:00:09</td>
                    </tr>
                  </tbody>
                </table>
                <input type="hidden" id="currentPage" value="2" />
                <input type="hidden" id="pageSize" value="10" />
              </form>
              <div class="pagination">
                <span>共有：13条记录</span>
              </div>
            </div>
            </body>
            </html>
        """.trimIndent()

        val page = parseMyTrainingReservationsPage(html)

        assertNotNull(page)
        assertEquals("2025-11-06", page?.beginDate)
        assertEquals("2026-04-1", page?.endDate)
        assertEquals(1, page?.pageNo)
        assertEquals(10, page?.pageSize)
        assertEquals(13, page?.totalCount)
        assertEquals(2, page?.items?.size)
        assertEquals("研讨室A710", page?.items?.first()?.roomName)
        assertEquals("已审核", page?.items?.get(1)?.status)
    }

    @Test
    fun `parse current reservation page extracts javascript lists`() {
        val html = """
            <html>
            <body>
            <a href="trainingroombeskinfor">我的研讨间预约</a>
            <script type="text/javascript">
              var oneroombesklist = [{
                "begintime":"09:00:00",
                "committime":"2026-04-04 08:10:00.000",
                "endtime":"10:00:00",
                "id":"one-1",
                "roomname":"研讨室A101",
                "useday":"2026-04-05"
              }];
              var moreroombesklist = [{
                "begintime":"14:18:37",
                "committime":"2026-04-04 15:21:59.533",
                "endtime":"17:18:55",
                "id":"1515122",
                "isCheck":1,
                "roomid":"126",
                "roomname":"研讨室A520",
                "useday":"2026-04-05",
                "useendday":"2026-04-05"
              }];
            </script>
            </body>
            </html>
        """.trimIndent()

        val items = parseMyCurrentTrainingReservations(html)

        assertEquals(2, items.size)
        assertEquals("单人预约", items[0].status)
        assertEquals("研讨室A101", items[0].roomName)
        assertEquals("2026-04-05", items[0].endDate)
        assertEquals("已审核", items[1].status)
        assertEquals("2026-04-04 15:21", items[1].createdAt)
        assertEquals("研讨室A520", items[1].roomName)
    }
}
