/*
 * This file is part of leaf-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2022-2024 aoqia, FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package dev.aoqia.leaf.loom.test.unit.download

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.time.Instant

import io.javalin.http.HttpStatus
import spock.lang.IgnoreIf

import io.javalin.http.HttpStatus
import spock.lang.IgnoreIf

import dev.aoqia.leaf.loom.util.download.Download
import dev.aoqia.leaf.loom.util.download.DownloadException
import dev.aoqia.leaf.loom.util.download.DownloadExecutor
import dev.aoqia.leaf.loom.util.download.DownloadProgressListener

import io.javalin.http.HttpStatus
import spock.lang.IgnoreIf

class DownloadFileTest extends DownloadTest {
	@IgnoreIf({
		os.windows
	})
	// Requires admin on windows.
	def "Directory: Symlink"() {
		setup:
		server.get("/symlinkFile") {
			it.result("Hello World")
		}
		def output = new File(File.createTempDir(), "file.txt").toPath()
		def linkedtmp = new File(File.createTempDir(), "linkedtmp").toPath()
		Files.createSymbolicLink(linkedtmp, output.getParent())
		def symlink = Paths.get(linkedtmp.toString(), "file.txt")

		when:
		def result = Download.create("$DownloadTest.PATH/symlinkFile").downloadPath(symlink)

		then:
		Files.readString(symlink) == "Hello World"
	}

	def "File: Simple"() {
		setup:
		server.get("/simpleFile") {
			it.result("Hello World")
		}

		def output = new File(File.createTempDir(), "subdir/file.txt").toPath()

		when:
		def result = Download.create("$DownloadTest.PATH/simpleFile").downloadPath(output)

		then:
		Files.readString(output) == "Hello World"
	}

	def "File: Not found"() {
		setup:
		server.get("/fileNotfound") {
			it.status(HttpStatus.NOT_FOUND)
		}

		def output = new File(File.createTempDir(), "file.txt").toPath()

		when:
		def result = Download.create("$DownloadTest.PATH/fileNotfound").downloadPath(output)

		then:
		def e = thrown DownloadException
		e.statusCode == 404
	}

	def "File: Server error"() {
		setup:
		server.get("/fileServerError") {
			it.status(HttpStatus.INTERNAL_SERVER_ERROR)
		}

		def output = new File(File.createTempDir(), "file.txt").toPath()

		when:
		def result = Download.create("$DownloadTest.PATH/fileServerError").downloadPath(output)

		then:
		def e = thrown DownloadException
		e.statusCode == 500
	}

	def "Cache: Sha1"() {
		setup:
		int requestCount = 0

		server.get("/sha1.txt") {
			it.result("Hello World")
			requestCount++
		}

		def output = new File(File.createTempDir(), "file.txt").toPath()

		when:
		for (i in 0..<2) {
			Download.create("$DownloadTest.PATH/sha1.txt")
					.sha1("0a4d55a8d778e5022fab701977c5d840bbc486d0")
					.downloadPath(output)
		}

		then:
		requestCount == 1
	}

	def "Invalid Sha1"() {
		setup:
		server.get("/sha1.invalid") {
			it.result("Hello World")
		}

		def output = new File(File.createTempDir(), "file.txt").toPath()

		when:
		Download.create("$DownloadTest.PATH/sha1.invalid")
				.sha1("d139cccf047a749691416ce385d3f168c1e28309")
				.downloadPath(output)

		then:
		// Ensure the file we downloaded with the wrong hash was deleted
		Files.notExists(output)
		thrown DownloadException
	}

	def "Offline"() {
		setup:
		int requestCount = 0

		server.get("/offline.txt") {
			it.result("Hello World")
			requestCount++
		}

		def output = new File(File.createTempDir(), "offline.txt").toPath()

		when:
		Download.create("$DownloadTest.PATH/offline.txt")
				.downloadPath(output)

		Download.create("$DownloadTest.PATH/offline.txt")
				.offline()
				.downloadPath(output)

		then:
		requestCount == 1
	}

	def "Max Age"() {
		setup:
		int requestCount = 0

		server.get("/maxage.txt") {
			it.result("Hello World")
			requestCount++
		}

		def output = new File(File.createTempDir(), "maxage.txt").toPath()

		when:
		Download.create("$DownloadTest.PATH/maxage.txt")
				.maxAge(Duration.ofDays(1))
				.downloadPath(output)

		Download.create("$DownloadTest.PATH/maxage.txt")
				.maxAge(Duration.ofDays(1))
				.downloadPath(output)

		def twoDaysAgo = Instant.now() - Duration.ofDays(2)
		Files.setLastModifiedTime(output, FileTime.from(twoDaysAgo))

		Download.create("$DownloadTest.PATH/maxage.txt")
				.maxAge(Duration.ofDays(1))
				.downloadPath(output)

		then:
		requestCount == 2
	}

	def "ETag"() {
		setup:
		int requestCount = 0

		server.get("/etag") {
			def clientEtag = it.req.getHeader("If-None-Match")

			def result = "Hello world"
			def etag = result.hashCode().toString()
			it.header("ETag", etag)

			if (clientEtag == etag) {
				// Etag matches, no need to send the data.
				it.status(HttpStatus.NOT_MODIFIED)
				return
			}

			it.result(result)
			requestCount++
		}

		def output = new File(File.createTempDir(), "etag.txt").toPath()

		when:
		for (i in 0..<3) {
			Download.create("$DownloadTest.PATH/etag")
					.etag(true)
					.downloadPath(output)
		}

		then:
		requestCount == 1
	}

	def "ETag with max age"() {
		setup:
		// The count of requests, can return early if the ETag matches.
		int requestCount = 0
		// The count of full downloads
		int downloadCount = 0

		server.get("/etag") {
			def clientEtag = it.req.getHeader("If-None-Match")

			def result = "Hello world"
			def etag = result.hashCode().toString()
			it.header("ETag", etag)

			requestCount++

			if (clientEtag == etag) {
				// Etag matches, no need to send the data.
				it.status(HttpStatus.NOT_MODIFIED)
				return
			}

			it.result(result)
			downloadCount++
		}

		def output = new File(File.createTempDir(), "etag.txt").toPath()

		when:
		for (i in 0..<3) {
			if (i == 2) {
				// On the third request, set the file to be 2 days old, forcing the etag to be checked.
				Files.setLastModifiedTime(output, FileTime.from(Instant.now() - Duration.ofDays(2)))
			}

			Download.create("$DownloadTest.PATH/etag")
					.etag(true)
					.maxAge(Duration.ofDays(1))
					.downloadPath(output)
		}

		then:
		requestCount == 2
		downloadCount == 1
	}

	def "Progress: File"() {
		setup:
		server.get("/progressFile") {
			it.result("Hello World")
		}

		def output = new File(File.createTempDir(), "file.txt").toPath()
		def started, ended = false

		when:
		Download.create("$DownloadTest.PATH/progressFile")
				.progress(new DownloadProgressListener() {
					@Override
					void onStart() {
						started = true
					}

					@Override
					void onProgress(long bytesTransferred, long contentLength) {
					}

					@Override
					void onEnd() {
						ended = true
					}
				})
				.downloadPath(output)

		then:
		started
		ended
	}

	def "Progress: String"() {
		setup:
		server.get("/progressFile") {
			it.result("Hello World")
		}

		def started, ended = false

		when:
		Download.create("$DownloadTest.PATH/progressFile")
				.progress(new DownloadProgressListener() {
					@Override
					void onStart() {
						started = true
					}

					@Override
					void onProgress(long bytesTransferred, long contentLength) {
					}

					@Override
					void onEnd() {
						ended = true
					}
				})
				.downloadString()

		then:
		started
		ended
	}

	def "File: Async"() {
		setup:
		server.get("/async1") {
			it.result("Hello World")
		}

		def dir = File.createTempDir().toPath()

		when:
		new DownloadExecutor(2).withCloseable {
			Download.create("$DownloadTest.PATH/async1").downloadPathAsync(dir.resolve("1.txt"), it)
			Download.create("$DownloadTest.PATH/async1").downloadPathAsync(dir.resolve("2.txt"), it)
			Download.create("$DownloadTest.PATH/async1").downloadPathAsync(dir.resolve("3.txt"), it)
			Download.create("$DownloadTest.PATH/async1").downloadPathAsync(dir.resolve("4.txt"), it)
		}

		then:
		Files.readString(dir.resolve("4.txt")) == "Hello World"
	}

	def "File: Async Error"() {
		setup:
		server.get("/async2") {
			it.result("Hello World")
		}

		def dir = File.createTempDir().toPath()

		when:
		new DownloadExecutor(2).withCloseable {
			Download.create("$DownloadTest.PATH/async2").downloadPathAsync(dir.resolve("1.txt"), it)
			Download.create("$DownloadTest.PATH/async2").downloadPathAsync(dir.resolve("2.txt"), it)
			Download.create("$DownloadTest.PATH/async2").downloadPathAsync(dir.resolve("3.txt"), it)
			Download.create("$DownloadTest.PATH/async2").downloadPathAsync(dir.resolve("4.txt"), it)

			Download.create("$DownloadTest.PATH/asyncError").downloadPathAsync(dir.resolve("5.txt"), it)
			Download.create("$DownloadTest.PATH/asyncError2").downloadPathAsync(dir.resolve("6.txt"), it)
		}

		then:
		thrown DownloadException
	}

	def "File: Large"() {
		setup:
		byte[] data = new byte[1024 * 1024 * 10]
		// 10MB
		new Random().nextBytes(data)

		server.get("/largeFile") {
			it.result(data)
		}

		def output = new File(File.createTempDir(), "file").toPath()

		when:
		def result = Download.create("$DownloadTest.PATH/largeFile").downloadPath(output)

		then:
		Files.readAllBytes(output) == data
	}

	def "File: Insecure protocol"() {
		setup:
		def output = new File(File.createTempDir(), "file").toPath()
		when:
		def result = Download.create("http://fabricmc.net").downloadPath(output)
		then:
		thrown IllegalArgumentException
	}
}
