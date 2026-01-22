#
# Data Broker Android App
# Phase 3.6: TCP/IP - No custom SELinux contexts needed
#

PRODUCT_PACKAGES += \
    DbApp \
    libdb_client_jni

# Phase 3.6: platform_app domain使用のためカスタムSELinuxポリシー不要
# BOARD_VENDOR_SEPOLICY_DIRS += \
#     vendor/db_app/sepolicy
