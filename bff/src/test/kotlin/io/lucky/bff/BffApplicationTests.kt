package io.lucky.bff

import io.kotest.core.spec.style.DescribeSpec
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BffApplicationTests : DescribeSpec({
    describe("BffApplication") {
        it("컨텍스트가 정상적으로 로드된다") {}
    }
})
