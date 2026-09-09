use super::*;
use std::sync::atomic::{AtomicU64, Ordering};

static NEXT_FIXTURE: AtomicU64 = AtomicU64::new(0);

struct Fixture(PathBuf);

impl Fixture {
    fn new() -> Self {
        let path = std::env::temp_dir().join(format!(
            "kiyori-grep-{}-{}",
            std::process::id(),
            NEXT_FIXTURE.fetch_add(1, Ordering::Relaxed)
        ));
        std::fs::create_dir(&path).unwrap();
        Self(path)
    }

    fn write(&self, name: &str, content: &[u8]) -> PathBuf {
        let path = self.0.join(name);
        std::fs::create_dir_all(path.parent().unwrap()).unwrap();
        std::fs::write(&path, content).unwrap();
        path
    }

    fn options(&self, path: PathBuf) -> SearchOptions {
        SearchOptions {
            path,
            patterns: vec!["needle".into()],
            file_pattern: "*".into(),
            case_insensitive: false,
            context_lines: 1,
            max_results: 100,
        }
    }
}

impl Drop for Fixture {
    fn drop(&mut self) {
        // 只清理由本测试独占创建的临时目录。
        std::fs::remove_dir_all(&self.0).unwrap();
    }
}

#[test]
fn mapped_root_file_preserves_line_numbers_and_context() {
    let fixture = Fixture::new();
    let path = fixture.write("ubuntu/root/build_final.py", b"before\nneedle\nafter\n");
    let response = run_search(fixture.options(path.clone())).unwrap();
    assert_eq!(response.files_searched, 1);
    assert_eq!(response.blocks.len(), 1);
    let block = &response.blocks[0];
    assert_eq!(block.file_path, path.to_string_lossy());
    assert_eq!(block.first_match_line, 2);
    assert_eq!(block.match_context, "before\nneedle\nafter");
}

#[test]
fn directory_filter_case_and_binary_detection_work() {
    let fixture = Fixture::new();
    fixture.write("src/a.py", b"NEEDLE\n");
    fixture.write("src/a.txt", b"needle\n");
    fixture.write("src/binary.py", b"needle\0\n");
    fixture.write("backup/old.py", b"needle\n");
    let mut options = fixture.options(fixture.0.clone());
    options.file_pattern = "*.py".into();
    options.case_insensitive = true;
    let response = run_search(options).unwrap();
    assert_eq!(response.blocks.len(), 1);
    assert!(response.blocks[0].file_path.ends_with("a.py"));
}

#[test]
fn no_match_is_success_and_missing_path_is_error() {
    let fixture = Fixture::new();
    let path = fixture.write("plain.py", b"other\n");
    assert!(run_search(fixture.options(path)).unwrap().blocks.is_empty());
    assert!(run_search(fixture.options(fixture.0.join("missing.py"))).is_err());
}

#[test]
fn malformed_regex_and_glob_are_errors() {
    let fixture = Fixture::new();
    let path = fixture.write("plain.py", b"needle\n");
    let mut options = fixture.options(path.clone());
    options.patterns = vec!["[".into()];
    assert!(run_search(options).is_err());
    let mut options = fixture.options(path);
    options.file_pattern = "[".into();
    assert!(run_search(options).is_err());
}

#[test]
fn explicit_file_with_glob_metacharacters_is_not_a_directory_filter() {
    let fixture = Fixture::new();
    let path = fixture.write("project a/文件[1].py", b"needle\n");
    assert_eq!(run_search(fixture.options(path)).unwrap().blocks.len(), 1);
}

#[test]
fn max_results_limits_files_and_zero_returns_empty() {
    let fixture = Fixture::new();
    fixture.write("a.py", b"needle\n");
    fixture.write("b.py", b"needle\n");
    let mut options = fixture.options(fixture.0.clone());
    options.max_results = 1;
    assert_eq!(run_search(options).unwrap().blocks.len(), 1);
    let mut options = fixture.options(fixture.0.clone());
    options.max_results = 0;
    assert!(run_search(options).unwrap().blocks.is_empty());
}
