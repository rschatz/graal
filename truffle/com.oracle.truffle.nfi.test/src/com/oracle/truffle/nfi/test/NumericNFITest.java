/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.nfi.test;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import com.oracle.truffle.nfi.types.NativeSimpleType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.LongFunction;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.core.Is.is;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NumericNFITest extends NFITest {

    public static final NativeSimpleType[] NUMERIC_TYPES = {
                    NativeSimpleType.UINT8, NativeSimpleType.SINT8,
                    NativeSimpleType.UINT16, NativeSimpleType.SINT16,
                    NativeSimpleType.UINT32, NativeSimpleType.SINT32,
                    NativeSimpleType.UINT64, NativeSimpleType.SINT64,
                    NativeSimpleType.FLOAT, NativeSimpleType.DOUBLE,
                    NativeSimpleType.POINTER
    };

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> ret = new ArrayList<>();
        for (NativeSimpleType type : NUMERIC_TYPES) {
            ret.add(new Object[]{type});
        }
        return ret;
    }

    @Parameter(0) public NativeSimpleType type;

    public void checkExpectedRet(long expected, Object arg) {
        Object value = arg;
        switch (type) {
            case UINT8:
            case SINT8:
                Assert.assertThat("return type", value, is(instanceOf(Byte.class)));
                break;
            case UINT16:
            case SINT16:
                Assert.assertThat("return type", value, is(instanceOf(Short.class)));
                break;
            case UINT32:
            case SINT32:
                Assert.assertThat("return type", value, is(instanceOf(Integer.class)));
                break;
            case UINT64:
            case SINT64:
                Assert.assertThat("return type", value, is(instanceOf(Long.class)));
                break;
            case FLOAT:
                Assert.assertThat("return type", value, is(instanceOf(Float.class)));
                break;
            case DOUBLE:
                Assert.assertThat("return type", value, is(instanceOf(Double.class)));
                break;
            case POINTER:
                Assert.assertThat("return type", value, is(instanceOf(TruffleObject.class)));
                TruffleObject obj = (TruffleObject) value;
                Assert.assertTrue("isBoxed", JavaInterop.isBoxed(obj));
                value = JavaInterop.unbox(obj);
                Assert.assertThat("unboxed pointer", value, is(instanceOf(Long.class)));
                break;
            default:
                Assert.fail();
        }
        Assert.assertEquals(expected, ((Number) value).longValue());
    }

    /**
     * Test all primitive types as argument and return type of native functions.
     */
    @Test
    public void testIncrement() {
        TruffleObject increment = lookupAndBind("increment_" + type, String.format("(%s):%s", type, type));
        Object ret = sendExecute(increment, 42);
        checkExpectedRet(43, ret);
    }

    /**
     * Test boxed primitive types as argument to native functions.
     */
    @Test
    public void testBoxed() {
        TruffleObject increment = lookupAndBind("increment_" + type, String.format("(%s):%s", type, type));
        Object ret = sendExecute(increment, JavaInterop.asTruffleObject(42));
        checkExpectedRet(43, ret);
    }

    /**
     * Test callback function as argument to native functions, and all primitive types as argument
     * and return type of callback functions.
     */
    @Test
    public void testCallback() {
        TruffleObject callback = JavaInterop.asTruffleFunction(LongFunction.class, (arg) -> arg + 5);
        TruffleObject function = lookupAndBind("callback_" + type, String.format("((%s):%s, %s) : %s", type, type, type, type));
        Object ret = sendExecute(function, callback, 42);
        checkExpectedRet((42 + 6) * 2, ret);
    }

    /**
     * Test callback function as return type of native function.
     */
    @Test
    public void testCallbackRet() {
        TruffleObject getIncrement = lookupAndBind("callback_ret_" + type, String.format("() : (%s):%s", type, type));
        Object functionPtr = sendExecute(getIncrement);
        Assert.assertThat("closure", functionPtr, is(instanceOf(TruffleObject.class)));

        Object ret = sendExecute((TruffleObject) functionPtr, 42);
        checkExpectedRet(43, ret);
    }

    @FunctionalInterface
    private interface WrapFunction {

        TruffleObject wrap(LongFunction<?> fn);
    }

    /**
     * Test callback functions as argument and return type of other callback functions.
     */
    @Test
    public void testPingPong() {
        String fnPointer = String.format("(%s):%s", type, type);
        String wrapPointer = String.format("(%s):%s", fnPointer, fnPointer);
        TruffleObject pingPong = lookupAndBind("pingpong_" + type, String.format("(%s, %s) : %s", wrapPointer, type, type));

        TruffleObject wrap = JavaInterop.asTruffleFunction(WrapFunction.class, (fn) -> {
            LongFunction<?> ret = (arg) -> fn.apply(arg * 3);
            return JavaInterop.asTruffleFunction(LongFunction.class, ret);
        });

        Object ret = sendExecute(pingPong, wrap, 5);
        checkExpectedRet(38, ret);
    }
}
