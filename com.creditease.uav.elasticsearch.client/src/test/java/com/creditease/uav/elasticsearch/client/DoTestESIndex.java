/*-
 * <<
 * UAVStack
 * ==
 * Copyright (C) 2016 - 2017 UAVStack
 * ==
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * >>
 */

/*-
 * <<
 * UAVStack
 * ==
 * Copyright (C) 2016 - 2017 UAVStack
 * ==
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * >>
 */
package com.creditease.uav.elasticsearch.client;

import com.creditease.uav.elasticsearch.index.ESIndexHelper;

/**
 * DoTestESIndex description: ����ESIndexHelper��
 *
 */
public class DoTestESIndex {

    public static void main(String[] args) {

        String prefix = "apm";
        String time = "2017-9-6";
        long timestamp = 1504627200000l;// 2017-9-6 ��ʱ���
        // ��ȡ���ܵ�����
        System.out.println("��ȡ���ܵ�������\t" + ESIndexHelper.getIndexOfWeek(prefix));
        // ��ȡ����ǰ���ܵ�����
        System.out.println("��ȡ����ǰ���ܵ�������\t" + ESIndexHelper.getIndexOfWeek(prefix, -2));
        // ���2017��9��6�����ܵ�����
        System.out.println("���2017��9��6�����ܵ�������(9.3)\t" + ESIndexHelper.getIndexOfWeek(prefix, time));
        // ��� 2017��9��6�յĺ����ܵ���������
        System.out.println("���2017��9��6�յ��ܵ�������(9.3)\t" + ESIndexHelper.getIndexOfWeekByMillis(prefix, timestamp));

        // ��ȡ���ܵ�����
        System.out.println("��ȡ���յ�������\t" + ESIndexHelper.getIndexOfDay(prefix));
        // ��ȡ����ǰ���ܵ�����
        System.out.println("��ȡ����ǰ�����������\t" + ESIndexHelper.getIndexOfDay(prefix, -2));
        // ���9��6�����ܵ�����
        System.out.println("���2017��9��6�������������\t" + ESIndexHelper.getIndexOfDay(prefix, time));
        // ���9��6�����ܵ�����
        System.out.println("���2017��9��6�յ����������\t" + ESIndexHelper.getIndexOfDayByMillis(prefix, timestamp));
    }

}
