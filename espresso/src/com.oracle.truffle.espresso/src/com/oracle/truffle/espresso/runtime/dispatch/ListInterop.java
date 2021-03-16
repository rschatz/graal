/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.runtime.dispatch;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.interop.InvokeEspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@ExportLibrary(value = InteropLibrary.class, receiverType = StaticObject.class)
public final class ListInterop extends EspressoInterop {
    @ExportMessage
    abstract static class GetArraySize {

        static final int LIMIT = 3;

        @Specialization(guards = {"receiver.getKlass() == cachedKlass"}, limit = "LIMIT")
        static long doCached(StaticObject receiver,
                        @Cached("receiver.getKlass()") Klass cachedKlass,
                        @Cached("doSizeLookup(receiver)") Method method,
                        @Cached("create(method.getCallTarget())") DirectCallNode callNode) {
            return (int) callNode.call(receiver);
        }

        @Specialization(replaces = "doCached")
        static long doUncached(StaticObject receiver,
                        @Cached IndirectCallNode invoke) {
            Method size = doSizeLookup(receiver);
            return (int) invoke.call(size.getCallTarget(), receiver);
        }

        static Method doSizeLookup(StaticObject receiver) {
            return receiver.getKlass().lookupMethod(Name.size, Signature._int);
        }
    }

    @ExportMessage
    static Object readArrayElement(StaticObject receiver, long index,
                    @Cached ListGet listGet,
                    @Cached @Shared("error") BranchProfile error) throws InvalidArrayIndexException {
        return listGet.listGet(receiver, index, error);
    }

    @ExportMessage
    static void writeArrayElement(StaticObject receiver, long index, Object value,
                    @Cached ListSet listSet,
                    @Cached @Shared("error") BranchProfile error) throws InvalidArrayIndexException {
        listSet.listSet(receiver, index, value, error);
    }

    @ExportMessage
    static boolean hasArrayElements(@SuppressWarnings("unused") StaticObject receiver) {
        return true;
    }

    @ExportMessage
    static boolean isArrayElementReadable(StaticObject receiver, long index) {
        return boundsCheck(receiver, index);
    }

    @ExportMessage
    static boolean isArrayElementModifiable(StaticObject receiver, long index) {
        return boundsCheck(receiver, index);
    }

    private static boolean boundsCheck(StaticObject receiver, long index) {
        Method size = receiver.getKlass().lookupMethod(Name.size, Signature._int);
        return 0 <= index && index < (int) size.invokeDirect(receiver);
    }

    @GenerateUncached
    abstract static class ListGet extends Node {
        static final int LIMIT = 3;

        public Object listGet(StaticObject receiver, long index, BranchProfile error) throws InvalidArrayIndexException {
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            try {
                return unwrapForeign(execute(receiver, (int) index));
            } catch (EspressoException e) {
                error.enter();
                if (InterpreterToVM.instanceOf(e.getExceptionObject(), receiver.getKlass().getMeta().java_lang_IndexOutOfBoundsException)) {
                    throw InvalidArrayIndexException.create(index);
                }
                throw e; // unexpected exception
            }
        }

        protected abstract Object execute(StaticObject receiver, int index);

        @Specialization(guards = {"receiver.getKlass() == cachedKlass"}, limit = "LIMIT")
        static Object doCached(StaticObject receiver, int index,
                        @Cached("receiver.getKlass()") Klass cachedKlass,
                        @Cached("doGetLookup(receiver)") Method method,
                        @Cached("create(method.getCallTarget())") DirectCallNode callNode) {
            return callNode.call(receiver, index);
        }

        @Specialization(replaces = "doCached")
        static Object doUncached(StaticObject receiver, int index,
                        @Cached IndirectCallNode invoke) {
            Method get = doGetLookup(receiver);
            return invoke.call(get.getCallTarget(), receiver, index);
        }

        static Method doGetLookup(StaticObject receiver) {
            return receiver.getKlass().lookupMethod(Name.get, Signature.Object_int);
        }
    }

    @GenerateUncached
    abstract static class ListSet extends Node {
        static final int LIMIT = 3;

        public void listSet(StaticObject receiver, long index, Object value, BranchProfile error) throws InvalidArrayIndexException {
            if (index < 0 || Integer.MAX_VALUE < index) {
                error.enter();
                throw InvalidArrayIndexException.create(index);
            }
            try {
                execute(receiver, (int) index, value);
            } catch (EspressoException e) {
                error.enter();
                if (InterpreterToVM.instanceOf(e.getExceptionObject(), receiver.getKlass().getMeta().java_lang_IndexOutOfBoundsException)) {
                    throw InvalidArrayIndexException.create(index);
                }
                throw e; // unexpected exception
            } catch (ArityException | UnsupportedTypeException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
            }
        }

        protected abstract void execute(StaticObject receiver, int index, Object value) throws ArityException, UnsupportedTypeException;

        @Specialization(guards = {"receiver.getKlass() == cachedKlass"}, limit = "LIMIT")
        static void doCached(StaticObject receiver, int index, Object value,
                        @Cached("receiver.getKlass()") Klass cachedKlass,
                        @Cached("doSetLookup(receiver)") Method method,
                        @Cached InvokeEspressoNode invoke) throws ArityException, UnsupportedTypeException {
            invoke.execute(method, receiver, new Object[]{index, value});
        }

        @Specialization(replaces = "doCached")
        static void doUncached(StaticObject receiver, int index, Object value,
                        @Cached InvokeEspressoNode invoke) throws ArityException, UnsupportedTypeException {
            Method set = doSetLookup(receiver);
            invoke.execute(set, receiver, new Object[]{index, value});
        }

        static Method doSetLookup(StaticObject receiver) {
            return receiver.getKlass().lookupMethod(Name.set, Signature.Object_int_Object);
        }
    }
}
