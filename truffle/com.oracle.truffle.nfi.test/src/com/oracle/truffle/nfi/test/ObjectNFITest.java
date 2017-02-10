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

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.java.JavaInterop;
import java.util.function.Supplier;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ObjectNFITest extends NFITest {

    private static TruffleObject nativeEnv;

    private static class TestObject {

        int intField;

        public TestObject() {
            this(0);
        }

        public TestObject(int value) {
            intField = value;
        }

        public int readField(String field) {
            Assert.assertEquals("field name", "intField", field);
            return intField;
        }

        public void writeField(String field, int value) {
            Assert.assertEquals("field name", "intField", field);
            intField = value;
        }
    }

    interface ReadIntField {

        public int read(TestObject obj, String field);
    }

    interface WriteIntField {

        public void write(TestObject obj, String field, int value);
    }

    @BeforeClass
    public static void initEnv() {
        TruffleObject createNewObject = JavaInterop.asTruffleFunction(Supplier.class, TestObject::new);
        TruffleObject readIntField = JavaInterop.asTruffleFunction(ReadIntField.class, TestObject::readField);
        TruffleObject writeIntField = JavaInterop.asTruffleFunction(WriteIntField.class, TestObject::writeField);

        TruffleObject initializeEnv = lookupAndBind("initialize_env", "( ():object, (object,string):sint32, (object,string,sint32):void ) : pointer");
        try {
            nativeEnv = (TruffleObject) ForeignAccess.sendExecute(Message.createExecute(3).createNode(), initializeEnv, createNewObject, readIntField, writeIntField);
        } catch (InteropException ex) {
            throw new AssertionError(ex);
        }
    }

    interface DeleteEnv {

        public void delete(TruffleObject env);
    }

    @AfterClass
    public static void deleteEnv() {
        TruffleObject deleteEnv = lookupAndBind("delete_env", "(pointer):void");
        JavaInterop.asJavaFunction(DeleteEnv.class, deleteEnv).delete(nativeEnv);
        nativeEnv = null;
    }

    @Test
    public void testCopyAndIncrement() {
        TruffleObject copyAndIncrement = lookupAndBind("copy_and_increment", "(pointer, object) : object");
        TestObject testArg = new TestObject(42);
        TruffleObject arg = JavaInterop.asTruffleObject(testArg);

        Object ret = sendExecute(copyAndIncrement, nativeEnv, arg);
        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));

        TruffleObject obj = (TruffleObject) ret;
        Assert.assertTrue("isJavaObject", JavaInterop.isJavaObject(TestObject.class, obj));

        TestObject testRet = JavaInterop.asJavaObject(TestObject.class, obj);
        Assert.assertNotSame("return value", testArg, testRet);
        Assert.assertEquals("intField", 43, testRet.intField);
    }

    @Test
    public void testKeepObject() {
        TruffleObject keepExistingObject = lookupAndBind("keep_existing_object", "(object):pointer");
        TruffleObject freeAndGetObject = lookupAndBind("free_and_get_object", "(pointer):object");
        TruffleObject freeAndGetContent = lookupAndBind("free_and_get_content", "(pointer, pointer):sint32");

        TestObject testArg = new TestObject(42);
        TruffleObject obj = JavaInterop.asTruffleObject(testArg);

        Object nativePtr1 = sendExecute(keepExistingObject, obj);
        Object nativePtr2 = sendExecute(keepExistingObject, obj);
        Object nativePtr3 = sendExecute(keepExistingObject, obj);

        Object ret = sendExecute(freeAndGetContent, nativeEnv, nativePtr1);
        Assert.assertEquals("return value", 42, (int) (Integer) ret);

        testArg.intField--;

        ret = sendExecute(freeAndGetContent, nativeEnv, nativePtr2);
        Assert.assertEquals("return value", 41, (int) (Integer) ret);

        ret = sendExecute(freeAndGetObject, nativePtr3);
        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));

        TruffleObject retObj = (TruffleObject) ret;
        Assert.assertTrue("isJavaObject", JavaInterop.isJavaObject(TestObject.class, retObj));
        Assert.assertSame("return value", testArg, JavaInterop.asJavaObject(TestObject.class, retObj));
    }

    @Test
    public void testKeepNewObject() {
        TruffleObject keepNewObject = lookupAndBind("keep_new_object", "(pointer):pointer");
        TruffleObject freeAndGetObject = lookupAndBind("free_and_get_object", "(pointer):object");

        Object nativePtr = sendExecute(keepNewObject, nativeEnv);
        Object ret = sendExecute(freeAndGetObject, nativePtr);

        Assert.assertThat("return value", ret, is(instanceOf(TruffleObject.class)));

        TruffleObject retObj = (TruffleObject) ret;
        Assert.assertTrue("isJavaObject", JavaInterop.isJavaObject(TestObject.class, retObj));
        Assert.assertEquals("intField", 8472, JavaInterop.asJavaObject(TestObject.class, retObj).intField);
    }
}
