// 루트 프로젝트 이름 설정
rootProject.name = "channel-manager"

// 서브 모듈 등록
// - channel-manager-kotlin: Kotlin으로 구현한 모듈
// - channel-manager-java: Java로 구현한 모듈
// - channel-manager-common: Flyway SQL, static 리소스 등 공유 자원 모듈
include("channel-manager-kotlin")
include("channel-manager-java")
include("channel-manager-common")
