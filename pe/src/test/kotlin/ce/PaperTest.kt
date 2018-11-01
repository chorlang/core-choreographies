package ce

import Utils.Companion.resolveArgs
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource

class PaperTest {
    @Test
    fun ex2() {
        val test = "c { def X {a!<pwd>; a&{ok: `value`?; stop, ko: X}} main {X}} | " +
                "a { def X {c?; `value`?; if `value` then c+ok; `value`+ok; stop else c+ko; `value`+ko; X} main {X}} | " +
                "`value` { def X {a!<`value`>; a&{ok: c!<t>; stop, ko:X}} main {X}}"
        val args = arrayListOf("-c", test)

        val actual = ChoreographyExtraction.main(args)
        val expected = "def X1 { c.pwd->a; `value`.`value`->a; if a.`value` then a->c[ok]; a->`value`[ok]; `value`.t->c; stop else a->c[ko]; a->`value`[ko]; X1 } main {X1}"

        assertEquals(expected, actual)
    }

    @Test
    fun ex4() {
        val test = "p { def X {q!<e1>; X} main {X}} | " +
                "q { def Y {p?; Y} main {Y}} | " +
                "r { def Z {`value`!<e2>; Z} main {Z}} | " +
                "`value` { def W {r?; W} main {W}}"
        val args = arrayListOf("-c", test)

        val actual = ChoreographyExtraction.main(args)
        val expected = "def X1 { r.e2->`value`; p.e1->q; X1 } main {p.e1->q; X1}"

        assertEquals(expected, actual)
    }

    @Test
    fun ex5() {
        val test = "p { def X {q!<e>; X} main {X}} | " +
                "q { def Y {p?; Y} main {Y}} | " +
                "r { main {`value`!<e2>; stop}} | " +
                "`value` { main {r?; stop}}"
        val args = arrayListOf("-c", test)

        val actual = ChoreographyExtraction.main(args)
        val expected = "def X1 { p.e->q; X1 } main {r.e2->`value`; X1}"

        assertEquals(expected, actual)
    }

    @Test
    fun l1() {
        val test = "p { def X {q!<e>; q!<e>; q!<e>; X} main {X}} | " +
                "q { def Y {p?; p?; Y} main {p?; Y}}"
        val args = arrayListOf("-c", test)

        val actual = ChoreographyExtraction.main(args)
        val expected = "def X1 { p.e->q; p.e->q; p.e->q; p.e->q; p.e->q; p.e->q; X1 } main {X1}"

        assertEquals(expected, actual)
    }

    @Test
    fun l2() {
        val test = "p { def X {q!<e>; Y} def Y {r!<e>; Z} def Z {q!<e>; X} main {X}} | " +
                "q { def W {p?; W} main {W}} | " +
                "r { def T {p?; T} main {T}}"
        val args = arrayListOf("-c", test)

        val actual = ChoreographyExtraction.main(args)
        val expected = "def X1 { p.e->r; p.e->q; p.e->q; X1 } main {p.e->q; X1}"

        assertEquals(expected, actual)
    }

    @Test
    fun ex8() { /* 2-bit protocol*/
        val test = "a { def X {b?;b!<0>;b?;b!<1>;X} main {b!<0>;b!<1>;X}} | " +
                "b { def Y {a?;a!<ack0>;a?;a!<ack1>;Y} main {Y}}"
        val args = arrayListOf("-c", test)

        val actual = ChoreographyExtraction.main(args)
        val expected = "def X1 { (a.1->b, b.ack0->a); (a.0->b, b.ack1->a); X1 } main {a.0->b; X1}"

        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @CsvFileSource(resources = arrayOf("/settings.csv"), numLinesToSkip = 1)
    fun buyerSeller(strategy: String, debugMode: Boolean){
        val test =
                "buyer{main{seller!<quote>; seller?; if ok then seller+accept; seller?; stop else seller+reject; stop}} | " +
                        "shipper{main{seller&{" +
                        "send: seller?; seller!<t>; stop," +
                        "wait: stop}}} | " +
                        "seller{main{buyer?; buyer!<quote>; buyer&{" +
                        "accept: shipper+send; shipper!<deliv>; shipper?; buyer!<details>; stop, " +
                        "reject: shipper+wait; stop}}}"

        val param = resolveArgs(strategy, debugMode)
        val args = param.first
        val strategy = param.second
        args.add(test)

        val actual = ChoreographyExtraction.main(args)
        val expected =
                "main {buyer.quote->seller; seller.quote->buyer; if buyer.ok then buyer->seller[accept]; seller->shipper[send]; seller.deliv->shipper; shipper.t->seller; seller.details->buyer; stop else buyer->seller[reject]; seller->shipper[wait]; stop}"
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @CsvFileSource(resources = arrayOf("/settings.csv"), numLinesToSkip = 1)
    fun buyerSellerRec(strategy: String, debugMode: Boolean){
        val test =
                "buyer{def X {seller?; if ok then seller+accept; seller?; stop else seller+reject; X} main {seller!<quote>; X}} | " +
                        "shipper{def X {seller&{" +
                        "send: seller?; seller!<t>; stop," +
                        "wait: X}} main {X}} | " +
                        "seller{def X {buyer!<quote>; buyer&{" +
                        "accept: shipper+send; shipper!<deliv>; shipper?; buyer!<details>; stop, " +
                        "reject: shipper+wait; X}} main {buyer?; X}}"

        val param = resolveArgs(strategy, debugMode)
        val args = param.first
        val strategy = param.second
        args.add(test)

        val actual = ChoreographyExtraction.main(args)
        val expected =
                "def X1 { if buyer.ok then buyer->seller[accept]; seller->shipper[send]; seller.deliv->shipper; shipper.t->seller; seller.details->buyer; stop else buyer->seller[reject]; seller->shipper[wait]; seller.quote->buyer; X1 } main {buyer.quote->seller; seller.quote->buyer; X1}"
        assertEquals(expected, actual)
    }


    @ParameterizedTest
    @CsvFileSource(resources = arrayOf("/settings.csv"), numLinesToSkip = 1)
    fun twoBuyersProtocol(strategy: String, debugMode: Boolean){
        val test =
                "buyer1{def X {seller!<book>; seller?; buyer2!<quote>; X} main {X}} | " +
                        "buyer2{def X {seller?; buyer1?; if ok then seller+accept; seller!<address>; seller?; X else seller+decline; X} main {X}} | " +
                        "seller{def X {buyer1?; buyer1!<quote>; buyer2!<quote>; buyer2&{accept: buyer2?; buyer2!<date>; X, decline: X}} main {X}}"

        val param = resolveArgs(strategy, debugMode)
        val args = param.first
        val strategy = param.second
        args.add(test)

        val actual = ChoreographyExtraction.main(args)
        val expected =
                "def X1 { buyer1.book->seller; seller.quote->buyer1; X2 } def X2 { seller.quote->buyer2; buyer1.quote->buyer2; if buyer2.ok then buyer2->seller[accept]; buyer2.address->seller; seller.date->buyer2; buyer1.book->seller; seller.quote->buyer1; X2 else buyer2->seller[decline]; X1 } main {X1}"
        assertEquals(expected, actual)
    }

    @ParameterizedTest
    @CsvFileSource(resources = arrayOf("/settings.csv"), numLinesToSkip = 1)
    fun streamingProtocol(strategy: String, debugMode: Boolean){
        val test =
                "kernel{def X{data?; key?; consumer!<xor>; X} main{X}} | " +
                        "data{def X{kernel!<data>; X} main{X}} | " +
                        "key{def X{kernel!<data>; X} main{X}} | " +
                        "consumer{def X{kernel?; X} main{X}}"

        val param = resolveArgs(strategy, debugMode)
        val args = param.first
        val strategy = param.second
        args.add(test)

        val actual = ChoreographyExtraction.main(args)
        val expected =
                "def X1 { data.data->kernel; key.data->kernel; kernel.xor->consumer; X1 } main {X1}"
        assertEquals(expected, actual)
    }


    @Test// (expected = NetworkExtraction.NoPossibleActionsException::class)
    fun InstrumentControllingFail(){
        val test =
                "user{def X{instrument+move; instrument+photo; instrument+quit; stop} " +
                        "main {operator!<high>; operator&{" +
                        "ok: X," +
                        "no: stop}}} | " +
                        "operator{main{user?; if ok then user+ok; stop else user+no; stop}} | " +
                        "instrument{def X{user&{" +
                        "move: X," +
                        "photo: X," +
                        "quit: stop}} main{X}}"

        val args = arrayListOf("-c", test)

        Assertions.assertThrows(NetworkExtraction.NoPossibleActionsException::class.java
        ) { ChoreographyExtraction.main(args) }

    }

    @ParameterizedTest
    @CsvFileSource(resources = arrayOf("/settings.csv"), numLinesToSkip = 1)
    fun InstrumentControlling(strategy: String, debugMode: Boolean){
        val test =
                "user{def X{instrument+move; instrument+photo; instrument+quit; stop} " +
                        "main {operator!<high>; operator&{" +
                        "ok: X," +
                        "no: stop}}} | " +
                        "operator{main{user?; if ok then user+ok; instrument+ok; stop else user+no; instrument+no; stop}} | " +
                        "instrument{def X{user&{" +
                        "move: X," +
                        "photo: X," +
                        "quit: stop}} main{ operator&{" +
                        "ok: X, " +
                        "no: stop}}}"

        val param = resolveArgs(strategy, debugMode)
        val args = param.first
        val strategy = param.second
        args.add(test)

        val actual = ChoreographyExtraction.main(args)
        val expected =
                "main {user.high->operator; if operator.ok then operator->user[ok]; operator->instrument[ok]; user->instrument[move]; user->instrument[photo]; user->instrument[quit]; stop else operator->user[no]; operator->instrument[no]; stop}"
        assertEquals(expected, actual)
    }
}