package com.trainingroom.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDateTime

class MyReservationsParserTest {

    @Test
    fun `parse training use log page extracts operation statuses`() {
        val html = """
            <html>
            <body>
            <form action="/traininguselog" id="frompost" name="frompost">
              <input type="text" id="begintime" name="begintime" value="2026-02-19" />
              <input type="text" id="endtime" name="endtime" value="2026-05-30" />
              <table class="table_type_7 responsive_table full_width t_align_l">
                <thead>
                  <tr>
                    <th>研讨间</th>
                    <th>创建时间</th>
                    <th>使用日期</th>
                    <th>开始时间</th>
                    <th>结束时间</th>
                    <th>操作类型</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td data-title="研讨间">研讨室A520</td>
                    <td data-title="创建时间">2026-04-04 18:40</td>
                    <td data-title="使用日期">2026-04-05</td>
                    <td data-title="开始时间">14:18:37</td>
                    <td data-title="结束时间">17:18:55</td>
                    <td data-title="操作类型">取消或未知</td>
                  </tr>
                  <tr>
                    <td data-title="研讨间">研讨室A710</td>
                    <td data-title="创建时间">2026-03-28 14:50</td>
                    <td data-title="使用日期">2026-03-28</td>
                    <td data-title="开始时间">14:50:08</td>
                    <td data-title="结束时间">17:29:15</td>
                    <td data-title="操作类型">借出</td>
                  </tr>
                </tbody>
              </table>
              <input type="hidden" id="currentPage" value="2" />
              <input type="hidden" id="pageSize" value="10" />
              <div class="pagination">
                <span>共有：22条记录</span>
              </div>
            </form>
            </body>
            </html>
        """.trimIndent()

        val page = parseMyTrainingUseLogPage(html)

        assertNotNull(page)
        assertEquals("2026-02-19", page?.beginDate)
        assertEquals("2026-05-30", page?.endDate)
        assertEquals(1, page?.pageNo)
        assertEquals(10, page?.pageSize)
        assertEquals(22, page?.totalCount)
        assertEquals(2, page?.items?.size)
        assertEquals("已取消", page?.items?.first()?.status)
        assertEquals("借出", page?.items?.get(1)?.status)
        assertEquals("2026-03-28", page?.items?.get(1)?.endDate)
        assertEquals("17:29:15", page?.items?.get(1)?.endTime)
    }

    @Test
    fun `display borrowed reservation as ended after end time`() {
        val item = MyTrainingReservationItem(
            status = "借出",
            roomName = "研讨室A710",
            createdAt = "2026-03-28 14:50",
            useDate = "2026-03-28",
            startTime = "14:50:08",
            endDate = "2026-03-28",
            endTime = "17:29:15"
        )

        val status = displayMyTrainingReservationStatus(
            item = item,
            now = LocalDateTime.of(2026, 3, 28, 17, 30)
        )

        assertEquals("已结束", status)
    }

    @Test
    fun `parse current reservation page extracts multi room javascript list`() {
        val html = """
            <html>
            <body>
            <a href="trainingroombeskinfor">我的研讨间预约</a>
            <script type="text/javascript">
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

        assertEquals(1, items.size)
        assertEquals("已审核", items[0].status)
        assertEquals("1515122", items[0].id)
        assertEquals(MyTrainingReservationCancelType.MULTI, items[0].cancelType)
        assertEquals("2026-04-04 15:21", items[0].createdAt)
        assertEquals("研讨室A520", items[0].roomName)
    }
}
