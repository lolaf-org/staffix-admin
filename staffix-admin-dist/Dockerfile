#
# Copyright © 2026 Lolaf.org
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Built from target/ after ./mvnw install. Mount config/ with your users or JAAS module; /data keeps the connectors
FROM eclipse-temurin:21-jre
RUN useradd --system --home-dir /opt/staffix-admin staffix && mkdir /data && chown staffix /data
COPY target/staffix-admin/staffix-admin /opt/staffix-admin
USER staffix
WORKDIR /opt/staffix-admin
EXPOSE 8080
VOLUME /data
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar staffix-admin.jar --staffix.admin.data-dir=/data"]
