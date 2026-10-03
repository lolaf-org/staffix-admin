DIST=staffix-admin-dist/target/staffix-admin/staffix-admin
java -Dloader.path=$DIST/plugins -jar $DIST/staffix-admin.jar "$@"
